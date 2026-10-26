package com.freellm.chat.data

import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.ModelDef
import com.freellm.chat.domain.ProviderDef
import com.freellm.chat.domain.Router
import com.freellm.chat.domain.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class ProviderStatus { NOT_CONFIGURED, READY, FAILED }

data class ProviderUi(
    val def: ProviderDef,
    val status: ProviderStatus,
    val enabled: Boolean,
    val hasKey: Boolean
)

class ProviderManager(
    private val catalog: CatalogRepository,
    private val settings: SettingsStore,
    private val secrets: SecretStore,
    private val client: OpenAiClient,
    private val router: Router
) {
    private val enabledIds: Flow<Set<String>> = combine(
        catalog.catalog,
        settings.enabled,
        settings.keylessOff,
        settings.avoidTraining
    ) { cat, on, off, _ ->
        on + cat.providers.filter { it.keyless && it.id !in off }.map { it.id }
    }

    val providers: Flow<List<ProviderUi>> = combine(
        catalog.catalog,
        enabledIds,
        settings.order,
        settings.statuses,
        secrets.changes
    ) { cat, enabled, order, statuses, _ ->
        val sorted = cat.providers
            .filter { it.available }
            .sortedBy { p -> order.indexOf(p.id).let { if (it < 0) Int.MAX_VALUE else it } }
        sorted.map { p ->
            val has = !secrets.apiKey(p.id).isNullOrBlank()
            val status = when {
                !has && !p.keyless -> ProviderStatus.NOT_CONFIGURED
                statuses[p.id] == "fail" -> ProviderStatus.FAILED
                else -> ProviderStatus.READY
            }
            ProviderUi(p, status, p.id in enabled, has)
        }
    }.flowOn(Dispatchers.Default)

    private fun expired(m: ModelDef, today: LocalDate): Boolean {
        val e = m.expiresAt ?: return false
        return try {
            LocalDate.parse(e.take(10)).isBefore(today)
        } catch (ex: Exception) {
            false
        }
    }

    private fun pathOf(url: String): String =
        "/" + url.substringAfter("://", "").substringAfter("/", "").trimEnd('/')

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore("/").lowercase()

    private fun viaProxy(id: String, url: String, proxy: String): String {
        val path = pathOf(url)
        return proxy.trim().trimEnd('/') + "/" + id + (if (path == "/") "" else path)
    }

    private fun baseFor(p: ProviderDef, custom: String?, proxy: String, accountId: String): Pair<String, String> {
        val c = custom.orEmpty().trim().trimEnd('/')
        val px = proxy.trim().trimEnd('/')
        val hasProxy = px.isNotEmpty()
        val direct = (if (c.isEmpty()) p.baseUrl.trimEnd('/') else c)
            .replace("{ACCOUNT_ID}", accountId.trim())
        val defaultDirect = p.baseUrl.trimEnd('/').replace("{ACCOUNT_ID}", accountId.trim())
        val knownHost = hostOf(direct) == hostOf(defaultDirect)
        val alreadyProxied = hasProxy && direct.startsWith(px)
        val customIsOtherProxy = c.isNotEmpty() && !knownHost && pathOf(c) == "/"
        return when {
            customIsOtherProxy -> viaProxy(p.id, defaultDirect, c) to ""
            alreadyProxied -> direct to ""
            hasProxy && knownHost -> viaProxy(p.id, direct, px) to direct
            else -> direct to ""
        }
    }

    suspend fun activeTargets(): List<Target> = withContext(Dispatchers.Default) {
        val cat = catalog.catalog.value
        val off = settings.keylessOff.first()
        val enabled = settings.enabled.first() + cat.providers.filter { it.keyless && it.id !in off }.map { it.id }
        val avoid = settings.avoidTraining.first()
        val order = settings.order.first()
        val statuses = settings.statuses.first()
        val today = LocalDate.now()
        val proxy = settings.proxyUrl.first()
        cat.providers
            .filter { it.available && it.id in enabled && statuses[it.id] != "fail" }
            .flatMap { p ->
                val key = secrets.apiKey(p.id).orEmpty()
                if (key.isBlank() && !p.keyless) {
                    emptyList()
                } else {
                    val rank = order.indexOf(p.id).let { if (it < 0) 99 else it }
                    val (base, alt) = baseFor(p, secrets.baseUrl(p.id), proxy, secrets.accountId(p.id).orEmpty())
                    cat.models
                        .filter { it.provider == p.id && !expired(it, today) && !(avoid && it.trainsOnData) }
                        .map { m -> Target(p.id, p.name, base, p.headers, key, m, rank, p.perModelLimits, alt, p.sharedLimits) }
                }
            }
    }

    suspend fun testAndSave(
        p: ProviderDef,
        key: String,
        baseOverride: String,
        accountId: String
    ): LlmException? {
        val k = key.trim()
        if (k.isEmpty() && !p.keyless) return LlmException(ErrorKind.AUTH, 401, message = "empty key")
        val proxy = settings.proxyUrl.first()
        val (base, alt) = baseFor(p, baseOverride, proxy, accountId)
        val models = catalog.catalog.value.models
            .filter { it.provider == p.id }
            .sortedBy { it.priority }
            .take(4)
        if (models.isEmpty()) return LlmException(ErrorKind.NOT_FOUND, 404, message = "no models")
        var last: LlmException? = null
        var accepted = false
        for (m in models) {
            val err = client.test(Target(p.id, p.name, base, p.headers, k, m, altBaseUrl = alt))
            if (err == null || err.kind == ErrorKind.RATE_LIMIT) {
                accepted = true
                break
            }
            last = err
            if (err.kind != ErrorKind.NOT_FOUND && err.kind != ErrorKind.SERVER) break
        }
        if (accepted) {
            withContext(Dispatchers.IO) {
                secrets.setApiKey(p.id, k)
                secrets.setBaseUrl(
                    p.id,
                    baseOverride.trim().takeUnless { it.trimEnd('/') == p.baseUrl.trimEnd('/') }.orEmpty()
                )
                secrets.setAccountId(p.id, accountId.trim())
            }
            settings.setEnabled(p.id, true)
            settings.setStatus(p.id, "ok")
            router.health.clearProvider(p.id)
            return null
        }
        val hasOld = withContext(Dispatchers.IO) { !secrets.apiKey(p.id).isNullOrBlank() }
        if (hasOld) settings.setStatus(p.id, "fail")
        return last
    }

    suspend fun setEnabled(id: String, on: Boolean) {
        settings.setEnabled(id, on)
        settings.setKeylessOff(id, !on)
        if (on) {
            settings.setStatus(id, "ok")
            router.health.clearProvider(id)
        }
    }

    suspend fun markFailed(id: String) {
        settings.setStatus(id, "fail")
        settings.setEnabled(id, false)
        settings.setKeylessOff(id, true)
    }

    suspend fun move(id: String, delta: Int) {
        val ids = providers.first().map { it.def.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= ids.size) return
        val tmp = ids[i]
        ids[i] = ids[j]
        ids[j] = tmp
        settings.setOrder(ids)
    }

    suspend fun removeKey(id: String) {
        withContext(Dispatchers.IO) { secrets.clear(id) }
        val keyless = catalog.catalog.value.providers.firstOrNull { it.id == id }?.keyless == true
        if (!keyless) {
            settings.setEnabled(id, false)
            settings.setStatus(id, null)
        }
        router.health.clearProvider(id)
    }

    fun baseUrlOf(id: String): String = secrets.baseUrl(id).orEmpty()
    fun accountIdOf(id: String): String = secrets.accountId(id).orEmpty()
}
