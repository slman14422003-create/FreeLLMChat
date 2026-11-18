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
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Inet4Address
import kotlinx.coroutines.flow.first
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class FreeLlmApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + com.freellm.chat.data.CrashLog.guard)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .dns(Ipv4FirstDns)
            .addInterceptor(com.freellm.chat.data.LanGuard.interceptor)
            .build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val backup: BackupManager by lazy { BackupManager(this, secrets, settings, db.dao(), attachments) }
    val autoBackup: com.freellm.chat.data.AutoBackup by lazy { com.freellm.chat.data.AutoBackup(this, backup, secrets, local) }
    val client: OpenAiClient by lazy { OpenAiClient(http) }
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

    private fun scheduleSave(r: Router) {
        if (!savePending.compareAndSet(false, true)) return
        appScope.launch {
            kotlinx.coroutines.delay(3_000)
            savePending.set(false)
            runCatching { usage.write(ROUTER_STATE_KEY, r.snapshot()) }
        }
    }

    fun warmUp() {
        val now = System.currentTimeMillis()
        if (now - lastWarm < 20_000) return
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
        appScope.launch { catalog.load() }
        appScope.launch {
            settings.fastSwitchRaw.collect {
                router.hedgeMs = if (it == false) 0L else 3_000L
                router.forceHedge = it == true
            }
        }
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: android.app.Activity) {
                if (started++ == 0) warmUp()
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        runCatching {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                private var vpn: Boolean? = null

                override fun onAvailable(network: android.net.Network) {
                    http.connectionPool.evictAll()
                    router.onNetworkChange()
                }

                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    val now = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                    val was = vpn
                    vpn = now
                    if (was != null && was != now) {
                        http.connectionPool.evictAll()
                        router.onNetworkChange()
                    }
                }

                override fun onLost(network: android.net.Network) {
                    http.connectionPool.evictAll()
                }
            })
        }
    }
}

private const val ROUTER_STATE_KEY = "router_state_v1"

object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
}
