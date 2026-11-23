package com.freellm.chat

import android.app.Application
import com.freellm.chat.data.AttachmentStore
import com.freellm.chat.data.BackupManager
import com.freellm.chat.data.BrainStore
import com.freellm.chat.data.CatalogRepository
import com.freellm.chat.data.UsagePrefs
import com.freellm.chat.domain.UsageLimiter
import com.freellm.chat.data.ChatRepository
import com.freellm.chat.data.OpenAiClient
import com.freellm.chat.data.ProviderManager
import com.freellm.chat.data.SecretStore
import com.freellm.chat.data.SettingsStore
import com.freellm.chat.data.db.AppDatabase
import com.freellm.chat.domain.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class FreeLlmApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + com.freellm.chat.data.CrashLog.guard)

    val netState: com.freellm.chat.data.NetState by lazy { com.freellm.chat.data.NetState(this) }
    val netDns: com.freellm.chat.data.NetDns by lazy { com.freellm.chat.data.NetDns(netState) }
    @Volatile var foreground: Boolean = false

    val workerAuth = com.freellm.chat.data.WorkerAuthInterceptor()

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 10
            })
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            .socketFactory(netState.socketFactory)
            .dns(netDns)
            .addInterceptor(com.freellm.chat.data.VpnGuard(netState))
            .addInterceptor(com.freellm.chat.data.LanGuard.interceptor)
            .addInterceptor(workerAuth)
            .build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val whisper: com.freellm.chat.data.dictation.WhisperModels by lazy { com.freellm.chat.data.dictation.WhisperModels(this, http) }
    val saver: com.freellm.chat.data.SaverState by lazy { com.freellm.chat.data.SaverState(this, settings, appScope) }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val backup: BackupManager by lazy { BackupManager(this, secrets, settings, db.dao(), attachments) }
    val autoBackup: com.freellm.chat.data.AutoBackup by lazy { com.freellm.chat.data.AutoBackup(this, backup, secrets, local) }
    val client: OpenAiClient by lazy { OpenAiClient(http) { netState.vpn } }
    val local: com.freellm.chat.data.LocalPrefs by lazy { com.freellm.chat.data.LocalPrefs(this) }
    val usage: UsagePrefs by lazy { UsagePrefs(this) }
    val ratings: com.freellm.chat.data.ModelRatings by lazy { com.freellm.chat.data.ModelRatings(usage) }
    val router: Router by lazy {
        Router(
            client,
            config = com.freellm.chat.domain.RouterConfig(firstTokenTimeoutMs = 10_000, idleTimeoutMs = 15_000, hedgeMs = 3_000),
            limiter = UsageLimiter(usage),
            failLog = com.freellm.chat.domain.FailureLog(usage)
        ).also { r ->
            r.onOutcome = { k, ok, ms -> ratings.outcome(k, ok, ms) }
            restoreRouter(r)
            r.onStateChanged = { scheduleSave(r) }
        }
    }
    val timing: com.freellm.chat.domain.TimingLog by lazy { com.freellm.chat.domain.TimingLog(usage) }
    private val savePending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastWarm = 0L

    private fun restoreRouter(r: Router) {
        runCatching { usage.read(ROUTER_STATE_KEY)?.let { r.restore(it) } }
        if (!r.stats.hasHistory()) {
            runCatching {
                val text = assets.open("health.json").bufferedReader().use { it.readText() }
                r.stats.seed(com.freellm.chat.domain.HealthSeed.parse(text))
            }
        }
    }

    fun saveRouterNow() {
        runCatching { usage.write(ROUTER_STATE_KEY, router.snapshot()) }
    }

    private fun scheduleSave(r: Router) {
        if (!savePending.compareAndSet(false, true)) return
        appScope.launch {
            kotlinx.coroutines.delay(5_000)
            savePending.set(false)
            runCatching { usage.write(ROUTER_STATE_KEY, r.snapshot()) }
        }
    }

    fun warmUp(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWarm < 20_000) return
        lastWarm = now
        appScope.launch { runCatching { manager.warmUp() } }
    }

    val sideTasks: com.freellm.chat.domain.SideTasks by lazy { com.freellm.chat.domain.SideTasks(client, router) }
    val searchHttp: OkHttpClient by lazy {
        http.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    }
    val searchQuota: com.freellm.chat.domain.SearchQuota by lazy { com.freellm.chat.domain.SearchQuota(usage) }
    val webSearch: com.freellm.chat.domain.WebSearchPipeline by lazy {
        val fetcher = com.freellm.chat.data.PageFetcher(http) { settings.proxyUrl.first() }
        com.freellm.chat.domain.WebSearchPipeline(
            sideTasks,
            com.freellm.chat.data.RoomSearchCache(db.dao()),
            searchQuota,
            { url -> fetcher.fetch(url) }
        )
    }
    val localSearch: com.freellm.chat.data.WebViewSearch by lazy { com.freellm.chat.data.WebViewSearch(this) }
    val comparer: com.freellm.chat.domain.Comparer by lazy { com.freellm.chat.domain.Comparer(router) }
    val catalog: CatalogRepository by lazy { CatalogRepository(this, http, BuildConfig.MODELS_URL) }
    val attachments: AttachmentStore by lazy { AttachmentStore(this) }
    val brainStore: BrainStore by lazy { BrainStore(this) }
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.dao()) }
    val workspace: com.freellm.chat.data.Workspace by lazy { com.freellm.chat.data.Workspace(java.io.File(filesDir, "workspaces")) }
    val toolHost: com.freellm.chat.data.AppToolHost by lazy { com.freellm.chat.data.AppToolHost(this) }
    val toolRunner: com.freellm.chat.domain.ToolRunner by lazy { com.freellm.chat.domain.ToolRunner(router, toolHost) }
    val manager: ProviderManager by lazy { ProviderManager(catalog, settings, secrets, client, router) }

    override fun onCreate() {
        super.onCreate()
        com.freellm.chat.data.CrashLog.init(filesDir)
        appScope.launch { settings.proxyUrl.collect { workerAuth.proxy = it } }
        saver.start()
        appScope.launch { catalog.load(refresh = !saver.active.value) }
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.fastSwitchRaw, saver.active) { f, s -> f to s }.collect { (it, saved) ->
                router.hedgeMs = if (it == false || saved) 0L else 3_000L
                router.forceHedge = it == true && !saved
            }
        }
        appScope.launch {
            settings.netPath.collect { netState.mode = it }
        }
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(240_000)
                if (foreground) warmUp(true)
            }
        }
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: android.app.Activity) {
                if (started++ == 0) warmUp()
                foreground = started > 0
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
                foreground = started > 0
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        netState.start {
            http.connectionPool.evictAll()
            netDns.clear()
            router.onNetworkChange()
            warmUp(true)
        }
    }
}

private const val ROUTER_STATE_KEY = "router_state_v1"
