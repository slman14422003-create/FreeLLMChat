package com.freellm.chat

import android.app.Application
import com.freellm.chat.data.CatalogRepository
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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FreeLlmApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val client: OpenAiClient by lazy { OpenAiClient(http) }
    val router: Router by lazy { Router(client) }
    val catalog: CatalogRepository by lazy { CatalogRepository(this, http, BuildConfig.MODELS_URL) }
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.dao()) }
    val manager: ProviderManager by lazy { ProviderManager(catalog, settings, secrets, client, router) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { catalog.load() }
    }
}
