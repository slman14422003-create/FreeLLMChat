package com.freellm.chat.data

import android.content.Context
import com.freellm.chat.domain.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object CatalogGuard {
    fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun checksumMatches(bytes: ByteArray, checksumFile: String?): Boolean {
        val expected = checksumFile?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase().orEmpty()
        if (expected.length != 64 || !expected.all { it in '0'..'9' || it in 'a'..'f' }) return false
        return sha256Hex(bytes) == expected
    }

    private fun hostOf(url: String): String =
        url.trim().substringAfter("://", "").substringBefore('/').substringBefore('?').substringAfter('@').substringBefore(':').lowercase()

    fun sanitize(remote: Catalog, bundled: Catalog?): Catalog {
        val known = bundled?.providers.orEmpty().associateBy { it.id }
        val safe = remote.providers.mapNotNull { p ->
            val own = known[p.id]
            when {
                p.custom -> p
                own != null && !own.custom && hostOf(p.baseUrl) != hostOf(own.baseUrl) -> own
                !p.baseUrl.trim().startsWith("https://", ignoreCase = true) && !LanGuard.isPrivateHost(hostOf(p.baseUrl)) -> null
                else -> p
            }
        }
        return remote.copy(providers = safe)
    }
}

class CatalogRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val url: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val cacheFile = File(context.filesDir, "models_cache.json")
    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog

    private fun parse(text: String): Catalog? = try {
        json.decodeFromString(Catalog.serializer(), text).takeIf { it.providers.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun readAssets(): Catalog? = try {
        parse(context.assets.open("models.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        null
    }

    private fun readCache(): Catalog? = try {
        if (cacheFile.exists()) parse(cacheFile.readText()) else null
    } catch (e: Exception) {
        null
    }

    suspend fun load(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (_catalog.value.providers.isEmpty()) {
                val bundled = readAssets()
                val cached = readCache()?.let { CatalogGuard.sanitize(it, bundled) }
                val initial = if (cached != null && (bundled == null || cached.version >= bundled.version)) cached else bundled ?: cached
                if (initial != null) _catalog.value = initial
                if (initial === bundled) cacheFile.delete()
            }
            val fresh = cacheFile.exists() &&
                System.currentTimeMillis() - cacheFile.lastModified() < TTL_MS
            if (force || !fresh) {
                try {
                    val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
                    val req = Request.Builder().url(url).build()
                    val raw = client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) r.body?.bytes() else null
                    }
                    val sum = if (raw == null) null else try {
                        client.newCall(Request.Builder().url(url + ".sha256").build()).execute().use { r ->
                            if (r.isSuccessful) r.body?.string() else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    val text = if (raw != null && CatalogGuard.checksumMatches(raw, sum)) String(raw, Charsets.UTF_8) else null
                    if (text != null) {
                        val bundledNow = readAssets()
                        val parsed = parse(text)?.let { CatalogGuard.sanitize(it, bundledNow) }
                        val floor = bundledNow?.version ?: 0
                        if (parsed != null && parsed.version >= floor) {
                            cacheFile.writeText(text)
                            _catalog.value = parsed
                        }
                    }
                } catch (e: Exception) {
                    // keep cached or bundled catalog
                }
            }
        }
    }

    companion object {
        private const val TTL_MS = 6L * 60 * 60 * 1000
    }
}
