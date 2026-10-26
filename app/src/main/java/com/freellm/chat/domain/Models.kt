package com.freellm.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ProviderDef(
    val id: String,
    val name: String,
    val baseUrl: String,
    val keyUrl: String = "",
    val steps: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val available: Boolean = true,
    val needsAccountId: Boolean = false,
    val perModelLimits: Boolean = false,
    val keyless: Boolean = false,
    val sharedLimits: Boolean = false
)

@Serializable
data class ModelDef(
    val id: String,
    val provider: String,
    val displayName: String,
    val priority: Int,
    val contextWindow: Int = 8000,
    val modes: List<String> = emptyList(),
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val trainsOnData: Boolean = false,
    val rpm: Int = 0,
    val rph: Int = 0,
    val rpd: Int = 0,
    val rpmo: Int = 0,
    val expiresAt: String? = null
)

@Serializable
data class Catalog(
    val version: Int = 1,
    val providers: List<ProviderDef> = emptyList(),
    val models: List<ModelDef> = emptyList()
)

data class ChatMsg(val role: String, val content: String, val images: List<String> = emptyList())

data class Target(
    val providerId: String,
    val providerName: String,
    val baseUrl: String,
    val headers: Map<String, String>,
    val apiKey: String,
    val model: ModelDef,
    val providerRank: Int = 0,
    val perModelLimits: Boolean = false,
    val altBaseUrl: String = "",
    val sharedLimits: Boolean = false
) {
    val key: String get() = "$providerId/${model.id}"
    override fun toString(): String = "Target($key)"
}

enum class ChatMode { AUTO, FAST, SMART, CODE, PINNED }

interface ChatTransport {
    fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?): Flow<String>
}

data class RouteRequest(
    val messages: List<ChatMsg>,
    val mode: ChatMode,
    val pinnedKey: String?,
    val candidates: List<Target>,
    val maxTokens: Int? = null,
    val preferredKey: String? = null,
    val adaptive: Boolean = false,
    val preferSpeed: Boolean = false,
    val needsVision: Boolean = false
)

sealed class RouteEvent {
    data class Started(val target: Target) : RouteEvent()
    data class Delta(val text: String) : RouteEvent()
    data class Switched(val target: Target) : RouteEvent()
    data object Restart : RouteEvent()
    data class AuthFailed(val providerId: String, val code: Int) : RouteEvent()
    data class Done(val target: Target) : RouteEvent()
    data class Failed(val message: String, val pinned: Boolean) : RouteEvent()
}
