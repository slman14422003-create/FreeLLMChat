@file:OptIn(ExperimentalCoroutinesApi::class)

package com.freellm.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.ChatMode
import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.RouteEvent
import com.freellm.chat.domain.RouteRequest
import com.freellm.chat.domain.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StreamUi(
    val text: String = "",
    val notes: List<String> = emptyList(),
    val target: Target? = null,
    val active: Boolean = false,
    val error: String? = null,
    val pinnedFailure: Boolean = false
)

class ChatViewModel(private val app: FreeLlmApp) : ViewModel() {
    private val repo = app.chatRepo
    private val manager = app.manager
    private val router = app.router

    val conversationId = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")
    val selection = MutableStateFlow("AUTO")
    val stream = MutableStateFlow<StreamUi?>(null)
    val notice = MutableStateFlow<String?>(null)
    private var job: Job? = null

    val conversations: StateFlow<List<ConversationEntity>> = query
        .flatMapLatest { repo.conversations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ConversationEntity>())

    val messages: StateFlow<List<MessageEntity>> = conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<MessageEntity>()) else repo.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MessageEntity>())

    val targets: StateFlow<List<Target>> = manager.providers
        .map { manager.activeTargets() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Target>())

    init {
        viewModelScope.launch {
            val saved = app.settings.selection.first()
            selection.value = if (saved.startsWith("PIN:")) "AUTO" else saved
        }
    }

    val generating: Boolean get() = job?.isActive == true

    private fun nonPin(): String = selection.value.takeIf { !it.startsWith("PIN:") } ?: "AUTO"

    private fun modeOf(sel: String): ChatMode = when {
        sel.startsWith("PIN:") -> ChatMode.PINNED
        sel == "FAST" -> ChatMode.FAST
        sel == "SMART" -> ChatMode.SMART
        sel == "CODE" -> ChatMode.CODE
        else -> ChatMode.AUTO
    }

    fun open(id: Long) {
        job?.cancel()
        stream.value = null
        conversationId.value = id
        viewModelScope.launch {
            val c = repo.conversation(id)
            selection.value = c?.pinnedModel?.let { "PIN:$it" } ?: nonPin()
        }
    }

    fun newChat() {
        job?.cancel()
        stream.value = null
        conversationId.value = null
        selection.value = nonPin()
    }

    fun select(sel: String) {
        selection.value = sel
        viewModelScope.launch {
            if (!sel.startsWith("PIN:")) app.settings.setSelection(sel)
            conversationId.value?.let { repo.setPinned(it, sel.removePrefix("PIN:").takeIf { _ -> sel.startsWith("PIN:") }) }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun dismissError() {
        stream.value = null
    }

    fun tryOther() {
        val cid = conversationId.value ?: return
        select("AUTO")
        stream.value = null
        start { generate(cid, null) }
    }

    fun rename(id: Long, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { repo.rename(id, t) }
    }

    fun delete(id: Long) {
        if (conversationId.value == id) newChat()
        viewModelScope.launch { repo.deleteConversation(id) }
    }

    fun export(id: Long, onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repo.exportMarkdown(id)) }
    }

    fun deleteMessage(m: MessageEntity) {
        viewModelScope.launch { repo.deleteMessage(m.id) }
    }

    fun regenerate(m: MessageEntity) {
        val cid = conversationId.value ?: return
        if (generating) return
        start {
            repo.deleteFrom(cid, m.id)
            generate(cid, m.modelKey)
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || generating) return
        start {
            var cid = conversationId.value
            if (cid == null) {
                val sel = selection.value
                cid = repo.newConversation(
                    t.replace("\n", " ").take(40),
                    if (sel.startsWith("PIN:")) sel.removePrefix("PIN:") else null
                )
                conversationId.value = cid
            }
            repo.addMessage(
                MessageEntity(
                    conversationId = cid,
                    role = "user",
                    content = t,
                    createdAt = System.currentTimeMillis()
                )
            )
            generate(cid, null)
        }
    }

    private fun start(block: suspend () -> Unit) {
        job = viewModelScope.launch { block() }
    }

    private suspend fun generate(cid: Long, exclude: String?) {
        val history = repo.messagesOnce(cid)
            .filter { it.role == "user" || it.role == "assistant" }
            .map { ChatMsg(it.role, it.content) }
        val all = manager.activeTargets()
        val sel = selection.value
        var mode = modeOf(sel)
        var cands = all
        if (exclude != null) {
            if (mode == ChatMode.PINNED) mode = ChatMode.AUTO
            val filtered = all.filter { it.key != exclude }
            if (filtered.isNotEmpty()) cands = filtered
        }
        val req = RouteRequest(
            messages = listOf(ChatMsg("system", SYSTEM_PROMPT)) + history,
            mode = mode,
            pinnedKey = if (mode == ChatMode.PINNED) sel.removePrefix("PIN:") else null,
            candidates = cands
        )

        val text = StringBuilder()
        val notes = mutableListOf<String>()
        var target: Target? = null
        var error: String? = null
        var pinnedFailure = false
        var lastPush = 0L

        fun push(force: Boolean = false) {
            val n = System.currentTimeMillis()
            if (force || n - lastPush >= 50) {
                lastPush = n
                stream.value = StreamUi(text.toString(), notes.toList(), target, true)
            }
        }

        stream.value = StreamUi(active = true)
        try {
            router.stream(req).collect { ev ->
                when (ev) {
                    is RouteEvent.Started -> {
                        target = ev.target
                        push(true)
                    }
                    is RouteEvent.Delta -> {
                        text.append(ev.text)
                        push()
                    }
                    is RouteEvent.Switched -> {
                        target = ev.target
                        notes.add(app.getString(R.string.switched_to, ev.target.model.displayName))
                        push(true)
                    }
                    is RouteEvent.Restart -> {
                        text.clear()
                        push(true)
                    }
                    is RouteEvent.AuthFailed -> {
                        val name = all.firstOrNull { it.providerId == ev.providerId }?.providerName ?: ev.providerId
                        manager.markFailed(ev.providerId)
                        notice.value = app.getString(R.string.provider_disabled_notice, name)
                    }
                    is RouteEvent.Done -> {
                        target = ev.target
                        push(true)
                    }
                    is RouteEvent.Failed -> {
                        error = ev.message
                        pinnedFailure = ev.pinned
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                val finalText = text.toString()
                val t = target
                if (finalText.isNotBlank()) {
                    repo.addMessage(
                        MessageEntity(
                            conversationId = cid,
                            role = "assistant",
                            content = finalText,
                            modelName = t?.model?.displayName,
                            providerName = t?.providerName,
                            modelKey = t?.key,
                            notes = notes.joinToString("\n").ifEmpty { null },
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                val err = error
                stream.value = if (err != null) StreamUi(error = err, pinnedFailure = pinnedFailure) else null
            }
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful assistant. Reply in the same language as the user's last message."
    }
}
