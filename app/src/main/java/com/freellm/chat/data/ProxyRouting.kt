package com.freellm.chat.data

import com.freellm.chat.domain.ProviderDef

object ProxyRouting {
    private fun pathOf(url: String): String =
        "/" + url.substringAfter("://", "").substringAfter("/", "").trimEnd('/')

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore("/").lowercase()

    private fun viaProxy(id: String, url: String, proxy: String): String {
        val path = pathOf(url)
        return proxy.trim().trimEnd('/') + "/" + id + (if (path == "/") "" else path)
    }

    fun baseFor(p: ProviderDef, custom: String?, proxy: String, accountId: String): Pair<String, String> {
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
}
