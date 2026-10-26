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

enum class KeyState { OK, INVALID, FAILING }

data class KeyCheck(val providerName: String, val state: KeyState)

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
            val configured = !p.custom || (!secrets.baseUrl(p.id).isNullOrBlank() && !secrets.modelId(p.id).isNullOrBlank())
            val status = when {
                !configured -> ProviderStatus.NOT_CONFIGURED
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
        if (p.custom) return custom.orEmpty().trim().trimEnd('/') to ""
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
                val keys = secrets.apiKey(p.id).orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (keys.isEmpty() && !p.keyless) {
                    emptyList()
                } else {
                    val rank = order.indexOf(p.id).let { if (it < 0) 99 else it }
                    val (base, alt) = baseFor(p, secrets.baseUrl(p.id), proxy, secrets.accountId(p.id).orEmpty())
                    val pool = if (p.custom) {
                        val mid = secrets.modelId(p.id).orEmpty()
                        if (mid.isBlank() || base.isBlank()) emptyList() else customModels(p, mid)
                    } else {
                        cat.models.filter { it.provider == p.id && !expired(it, today) && !(avoid && it.trainsOnData) }
                    }
                    keys.ifEmpty { listOf("") }.flatMapIndexed { i, key ->
                        val pid = if (i == 0) p.id else p.id + "#" + (i + 1)
                        pool.map { m -> Target(pid, p.name, base, p.headers, key, m, rank, p.perModelLimits, alt, p.sharedLimits) }
                    }
                }
            }
    }

    private fun customModels(p: ProviderDef, ids: String): List<ModelDef> =
        ids.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().mapIndexed { i, id -> customModel(p, id, 95 + i) }

    private fun customModel(p: ProviderDef, id: String, priority: Int = 95) = ModelDef(
        id = id,
        provider = p.id,
        displayName = id,
        priority = priority,
        contextWindow = 32000,
        vision = Regex("llava|vision|minicpm-v|gemma3|qwen2.5vl|llama3.2-vision", RegexOption.IGNORE_CASE).containsMatchIn(id),
        modes = listOf("fast", "smart", "code")
    )

    private suspend fun probe(p: ProviderDef, base: String, alt: String, key: String, models: List<ModelDef>): LlmException? {
        var last: LlmException? = null
        for (m in models) {
            val err = client.test(Target(p.id, p.name, base, p.headers, key, m, altBaseUrl = alt))
            if (err == null || err.kind == ErrorKind.RATE_LIMIT) return null
            last = err
            if (err.kind != ErrorKind.NOT_FOUND && err.kind != ErrorKind.SERVER) break
        }
        return last
    }

    suspend fun testAndSave(
        p: ProviderDef,
        key: String,
        baseOverride: String,
        accountId: String,
        modelId: String = ""
    ): LlmException? {
        val keys = key.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty() && !p.keyless) return LlmException(ErrorKind.AUTH, 401, message = "empty key")
        val proxy = settings.proxyUrl.first()
        val (base, alt) = baseFor(p, baseOverride, proxy, accountId)
        val models = if (p.custom) {
            val mid = modelId.trim()
            if (base.isBlank() || mid.isEmpty()) return LlmException(ErrorKind.BAD_REQUEST, message = "base url and model id required")
            customModels(p, mid).take(1)
        } else {
            catalog.catalog.value.models
                .filter { it.provider == p.id }
                .sortedBy { it.priority }
                .take(4)
        }
        if (models.isEmpty()) return LlmException(ErrorKind.NOT_FOUND, 404, message = "no models")
        val good = ArrayList<String>()
        var last: LlmException? = null
        for (k in keys.ifEmpty { listOf("") }) {
            val err = probe(p, base, alt, k, models)
            if (err == null) good.add(k) else last = err
        }
        if (good.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                secrets.setApiKey(p.id, good.joinToString("\n"))
                secrets.setBaseUrl(
                    p.id,
                    baseOverride.trim().takeUnless { it.trimEnd('/') == p.baseUrl.trimEnd('/') }.orEmpty()
                )
                secrets.setAccountId(p.id, accountId.trim())
                if (p.custom) secrets.setModelId(p.id, modelId.trim())
            }
            settings.setEnabled(p.id, true)
            settings.setKeylessOff(p.id, false)
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

    suspend fun setOrder(ids: List<String>) {
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

    suspend fun testAll(): List<KeyCheck> {
        val cat = catalog.catalog.value
        val proxy = settings.proxyUrl.first()
        val out = ArrayList<KeyCheck>()
        for (p in cat.providers.filter { it.available }) {
            val keys = withContext(Dispatchers.IO) { secrets.apiKey(p.id).orEmpty() }
                .split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val (base, alt) = baseFor(p, secrets.baseUrl(p.id), proxy, secrets.accountId(p.id).orEmpty())
            val models = if (p.custom) {
                val mid = secrets.modelId(p.id).orEmpty()
                if (base.isBlank() || mid.isBlank()) continue
                customModels(p, mid).take(1)
            } else {
                if (keys.isEmpty()) continue
                cat.models.filter { it.provider == p.id }.sortedBy { it.priority }.take(2)
            }
            if (models.isEmpty()) continue
            var worst = KeyState.OK
            for (k in keys.ifEmpty { listOf("") }) {
                val err = probe(p, base, alt, k, models)
                val st = when {
                    err == null -> KeyState.OK
                    err.kind == ErrorKind.AUTH && err.httpCode == 401 -> KeyState.INVALID
                    else -> KeyState.FAILING
                }
                if (st.ordinal > worst.ordinal) worst = st
            }
            out.add(KeyCheck(p.name, worst))
        }
        return out
    }

    suspend fun discoverModels(base: String, key: String) = client.listModels(base, key)

    fun baseUrlOf(id: String): String = secrets.baseUrl(id).orEmpty()
    fun modelIdOf(id: String): String = secrets.modelId(id).orEmpty()
    fun accountIdOf(id: String): String = secrets.accountId(id).orEmpty()
}
