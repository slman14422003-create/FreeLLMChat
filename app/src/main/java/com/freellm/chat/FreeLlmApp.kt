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
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(4, 20, TimeUnit.SECONDS))
            .dns(Ipv4FirstDns)
            .addInterceptor(com.freellm.chat.data.LanGuard.interceptor)
            .build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val backup: BackupManager by lazy { BackupManager(this, secrets, settings) }
    val autoBackup: com.freellm.chat.data.AutoBackup by lazy { com.freellm.chat.data.AutoBackup(this, backup, secrets, local) }
    val client: OpenAiClient by lazy { OpenAiClient(http) }
    val local: com.freellm.chat.data.LocalPrefs by lazy { com.freellm.chat.data.LocalPrefs(this) }
    val usage: UsagePrefs by lazy { UsagePrefs(this) }
    val ratings: com.freellm.chat.data.ModelRatings by lazy { com.freellm.chat.data.ModelRatings(usage) }
    val router: Router by lazy {
        Router(
            client,
            config = com.freellm.chat.domain.RouterConfig(firstTokenTimeoutMs = 18_000, idleTimeoutMs = 15_000, hedgeMs = 6_000),
            limiter = UsageLimiter(usage),
            failLog = com.freellm.chat.domain.FailureLog(usage)
        ).also { r ->
            r.onOutcome = { k, ok, ms -> ratings.outcome(k, ok, ms) }
        }
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
    val groqVoice: com.freellm.chat.data.GroqVoice by lazy { com.freellm.chat.data.GroqVoice(this) }
    val manager: ProviderManager by lazy { ProviderManager(catalog, settings, secrets, client, router) }

    override fun onCreate() {
        super.onCreate()
        com.freellm.chat.data.CrashLog.init(filesDir)
        appScope.launch { catalog.load() }
        appScope.launch { settings.fastSwitch.collect { router.hedgeMs = if (it) 6_000L else 0L } }
        runCatching {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    http.connectionPool.evictAll()
                    router.health.clearTransient()
                }

                override fun onLost(network: android.net.Network) {
                    http.connectionPool.evictAll()
                }
            })
        }
    }
}

object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
}
