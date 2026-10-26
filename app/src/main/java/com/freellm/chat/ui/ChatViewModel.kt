@file:OptIn(ExperimentalCoroutinesApi::class)

package com.freellm.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freellm.chat.FreeLlmApp
import android.net.Uri
import com.freellm.chat.R
import com.freellm.chat.data.Attachment
import com.freellm.chat.data.AttachmentCodec
import com.freellm.chat.data.PdfExport
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.ChatMode
import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.RouteEvent
import com.freellm.chat.domain.Overlap
import com.freellm.chat.domain.RouteRequest
import com.freellm.chat.domain.TaskClassifier
import com.freellm.chat.domain.TaskKind
import com.freellm.chat.domain.TokenSaver
import com.freellm.chat.domain.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
    val pinnedFailure: Boolean = false,
    /** Set when a model is continuing an existing assistant message (hand-off). */
    val continuingId: Long? = null
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
    val pending = MutableStateFlow<List<Attachment>>(emptyList())
    val importing = MutableStateFlow(false)
    private var job: Job? = null
    private var lastError: String? = null
    private var lastPinned = false
    private var skipLocalOnce = false
    private val brain get() = app.brainStore.brain

    val conversations: StateFlow<List<ConversationEntity>> = query
        .flatMapLatest { repo.conversations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ConversationEntity>())

    val messages: StateFlow<List<MessageEntity>> = conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<MessageEntity>()) else repo.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MessageEntity>())

    val targets: StateFlow<List<Target>> = manager.providers
        .map { manager.activeTargets().filter { t -> '#' !in t.providerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Target>())

    init {
        viewModelScope.launch {
            val saved = app.settings.selection.first()
            selection.value = if (saved.startsWith("PIN:")) "ADAPT" else saved
        }
    }

    val generating: Boolean get() = job?.isActive == true

    fun health(t: Target): ModelHealth = router.health.state(t, System.currentTimeMillis())

    private fun nonPin(): String = selection.value.takeIf { !it.startsWith("PIN:") } ?: "ADAPT"

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

    /** Re-run the saved, unanswered request. [sel] optionally switches the model first. */
    fun retryPending(sel: String? = null) {
        val cid = conversationId.value ?: return
        if (generating) return
        if (sel != null) select(sel)
        stream.value = null
        start { runAdaptive(cid, null) }
    }

    /** Same as before: leave a failed pinned model and let the router pick another. */
    fun tryOther() {
        val cid = conversationId.value ?: return
        select("AUTO")
        stream.value = null
        start { runAdaptive(cid, null) }
    }

    /**
     * Hand the conversation to another model. If a reply is streaming it is stopped (the
     * partial text is kept) and the chosen model continues from exactly that point.
     */
    fun continueWith(sel: String?) {
        val cid = conversationId.value ?: return
        viewModelScope.launch {
            job?.cancelAndJoin()
            val last = repo.messagesOnce(cid).lastOrNull { it.role == "user" || it.role == "assistant" }
            if (sel != null) select(sel)
            stream.value = null
            if (last == null) return@launch
            if (last.role == "assistant") {
                start { runAdaptive(cid, if (sel == null) last.modelKey else null, last) }
            } else {
                start { runAdaptive(cid, null) }
            }
        }
    }

    /** Thumbs up/down on an answer: good answers are trusted more, bad ones are forgotten by the local brain. */
    fun rate(m: MessageEntity, good: Boolean) {
        viewModelScope.launch {
            val list = repo.messagesOnce(m.conversationId)
            val idx = list.indexOfFirst { it.id == m.id }
            val q = list.take(if (idx < 0) list.size else idx).lastOrNull { it.role == "user" }?.content ?: return@launch
            if (good) {
                brain.learn(q, m.content)?.let { brain.feedback(it.id, true) }
                notice.value = app.getString(R.string.brain_thanks)
            } else {
                brain.forgetQuestion(q)
                notice.value = app.getString(R.string.brain_forgot)
            }
            app.brainStore.save()
        }
    }

    fun clearBrain() {
        viewModelScope.launch {
            brain.clear()
            app.brainStore.save()
            notice.value = app.getString(R.string.brain_cleared)
        }
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

    fun regenerate(m: MessageEntity, sel: String? = null) {
        val cid = conversationId.value ?: return
        if (generating) return
        if (sel != null) select(sel)
        skipLocalOnce = true
        start {
            repo.deleteFrom(cid, m.id)
            runAdaptive(cid, if (sel == null) m.modelKey else null)
        }
    }

    /** Edit a sent message: everything after it is dropped and the new text is re-sent. */
    fun edit(m: MessageEntity, newText: String) {
        val t = newText.trim()
        val cid = conversationId.value ?: return
        if (t.isEmpty() || generating) return
        start {
            repo.deleteFrom(cid, m.id)
            repo.addMessage(
                MessageEntity(
                    conversationId = cid,
                    role = "user",
                    content = t,
                    createdAt = System.currentTimeMillis(),
                    attachments = m.attachments
                )
            )
            runAdaptive(cid, null)
        }
    }

    fun deleteAllChats() {
        job?.cancel()
        stream.value = null
        conversationId.value = null
        viewModelScope.launch { repo.deleteAll() }
    }

    private fun addPending(r: com.freellm.chat.data.ImportResult) {
        if (r.items.isNotEmpty()) pending.value = (pending.value + r.items).takeLast(MAX_PENDING)
        r.notice?.let { notice.value = it }
    }

    fun attachImages(uris: List<Uri>) {
        viewModelScope.launch {
            importing.value = true
            try {
                for (u in uris.take(MAX_PENDING)) {
                    val a = app.attachments.importImage(u)
                    if (a != null) addPending(com.freellm.chat.data.ImportResult(listOf(a)))
                    else notice.value = app.getString(R.string.attach_unsupported)
                }
            } finally {
                importing.value = false
            }
        }
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            try {
                addPending(
                    app.attachments.importFile(
                        uri,
                        app.getString(R.string.attach_no_text),
                        app.getString(R.string.attach_unsupported),
                        app.getString(R.string.attach_scanned)
                    )
                )
            } finally {
                importing.value = false
            }
        }
    }

    fun attachPdfAsImages(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            try {
                addPending(app.attachments.importPdfAsImages(uri, app.getString(R.string.attach_unsupported)))
            } finally {
                importing.value = false
            }
        }
    }

    fun removePending(a: Attachment) {
        pending.value = pending.value.filter { it.path != a.path }
    }

    fun savePdf(uri: Uri, text: String?, images: List<Attachment>) {
        viewModelScope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val out = app.contentResolver.openOutputStream(uri) ?: return@withContext false
                    out.use {
                        if (text != null) PdfExport.fromText(text, it)
                        else PdfExport.fromImages(images.map { a -> app.attachments.file(a) }, it)
                    }
                    true
                } catch (e: Throwable) {
                    false
                }
            }
            notice.value = app.getString(if (ok) R.string.pdf_saved else R.string.pdf_failed)
        }
    }

    private suspend fun toChat(list: List<MessageEntity>): List<ChatMsg> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            var budget = MAX_HISTORY_IMAGES
            list.asReversed().map { m ->
                val atts = AttachmentCodec.decode(m.attachments)
                if (m.role != "user" || atts.isEmpty()) {
                    ChatMsg(m.role, m.content)
                } else {
                    val imgs = ArrayList<String>()
                    for (a in atts.filter { it.isImage }.asReversed()) {
                        if (budget > 0) {
                            app.attachments.dataUrl(a)?.let {
                                imgs.add(0, it)
                                budget--
                            }
                        }
                    }
                    val sb = StringBuilder()
                    atts.filter { !it.isImage }.forEach { f ->
                        sb.append("[File: ").append(f.name).append("]\n")
                            .append(app.attachments.readText(f)).append("\n[End of file]\n\n")
                    }
                    sb.append(m.content)
                    ChatMsg("user", sb.toString(), imgs)
                }
            }.asReversed()
        }

    fun send(text: String) {
        val t0 = text.trim()
        val atts = pending.value
        if ((t0.isEmpty() && atts.isEmpty()) || generating || importing.value) return
        val t = if (t0.isEmpty()) {
            app.getString(if (atts.any { it.isImage }) R.string.default_prompt_image else R.string.default_prompt_file)
        } else t0
        pending.value = emptyList()
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
                    createdAt = System.currentTimeMillis(),
                    attachments = AttachmentCodec.encode(atts)
                )
            )
            runAdaptive(cid, null)
        }
    }

    private fun start(block: suspend () -> Unit) {
        job = viewModelScope.launch { block() }
    }

    private suspend fun systemPrompt(): String {
        val style = when (app.settings.style.first()) {
            "concise" -> "Keep answers short and direct."
            "detailed" -> "Give thorough, well-structured, detailed answers."
            "teacher" -> "Explain step by step like a patient teacher, using simple examples."
            else -> ""
        }
        val custom = app.settings.instructions.first().trim()
        return listOf(
            SYSTEM_PROMPT,
            style,
            if (custom.isNotEmpty()) "User instructions: $custom" else ""
        ).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun joinSep(prefix: String, next: String): String {
        val a = prefix.lastOrNull() ?: return ""
        val b = next.firstOrNull() ?: return ""
        if (a.isWhitespace() || b.isWhitespace()) return ""
        if (b in ".,;:!?)]}،؛؟") return ""
        return " "
    }

    private suspend fun runAdaptive(cid: Long, exclude: String?, cont: MessageEntity? = null) {
        lastError = null
        generate(cid, exclude, cont)
        if (selection.value != "ADAPT" || !app.settings.autoResume.first()) return
        var tries = 0
        try {
            while (lastError != null && !lastPinned && tries < MAX_RESUME) {
                tries++
                notice.value = app.getString(R.string.auto_resume_notice, tries)
                stream.value = StreamUi(active = true)
                delay(RESUME_DELAY_MS * tries)
                val last = repo.messagesOnce(cid).lastOrNull { it.role == "user" || it.role == "assistant" } ?: return
                lastError = null
                if (last.role == "assistant") generate(cid, null, last) else generate(cid, null)
            }
        } catch (e: CancellationException) {
            stream.value = null
            throw e
        }
    }

    private suspend fun generate(cid: Long, exclude: String?, cont: MessageEntity? = null) {
        val stored = repo.messagesOnce(cid)
            .filter { it.role == "user" || it.role == "assistant" }
            .let { l -> if (cont != null) l.filter { it.id <= cont.id } else l }
        var history = toChat(stored).toMutableList()
        val prefix = cont?.content.orEmpty()
        if (cont != null) history.add(ChatMsg("user", CONTINUE_PROMPT))

        val all = manager.activeTargets()
        val sel = selection.value
        val adaptive = sel == "ADAPT"
        val saver = app.settings.tokenSaver.first()
        val lastUser = stored.lastOrNull { it.role == "user" }?.content.orEmpty()
        val kind = TaskClassifier.classify(lastUser)
        val lastUserMsg = stored.lastOrNull { it.role == "user" }
        val hasAtt = lastUserMsg?.let { AttachmentCodec.decode(it.attachments).isNotEmpty() } == true
        val skipLocal = skipLocalOnce
        skipLocalOnce = false
        if (cont == null && exclude == null && !skipLocal && !hasAtt && stored.lastOrNull()?.role == "user" &&
            app.settings.brainLocalFirst.first()
        ) {
            val hit = brain.best(lastUser, LOCAL_CONFIDENT)
            if (hit != null) {
                brain.markUsed(hit.entry.id)
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = hit.entry.answer,
                        modelName = app.getString(R.string.local_brain_name),
                        providerName = app.getString(R.string.local_brain_provider),
                        modelKey = null,
                        notes = app.getString(R.string.local_answer_note, (hit.similarity * 100).toInt()),
                        createdAt = System.currentTimeMillis()
                    )
                )
                lastError = null
                lastPinned = false
                stream.value = null
                app.appScope.launch { app.brainStore.save() }
                return
            }
        }
        var saved = 0
        if (saver) {
            val c = TokenSaver.compact(history, kind)
            history = c.messages.toMutableList()
            saved = c.savedTokens
        }
        val needsVision = history.any { it.images.isNotEmpty() }
        var mode = modeOf(sel)
        if (adaptive) mode = kind.mode
        if (needsVision && mode == ChatMode.PINNED &&
            all.firstOrNull { it.key == sel.removePrefix("PIN:") }?.model?.vision != true
        ) mode = ChatMode.AUTO
        var cands = all
        var preferred: String? = null
        if (exclude != null) {
            if (mode == ChatMode.PINNED) mode = ChatMode.AUTO
            val filtered = all.filter { it.key != exclude }
            if (filtered.isNotEmpty()) cands = filtered
        } else if (mode == ChatMode.PINNED && app.settings.autoSwitch.first()) {
            // Pinned model first, but fall through to the next one when its free plan runs out.
            preferred = sel.removePrefix("PIN:")
            mode = ChatMode.AUTO
        }
        val hint = if (adaptive || saver) kind.hint else ""
        val deep = if (app.settings.deepThink.first() && kind != TaskKind.QUICK) DEEP_THINK_PROMPT else ""
        val memory = if (cont == null && !hasAtt && app.settings.brainContext.first()) {
            val m = brain.search(lastUser, 2, 0.5).filter { it.entry.answer.length <= 3000 }
            if (m.isEmpty()) "" else buildString {
                append("Earlier answers from this user's own history (reuse only if relevant; they may be outdated):")
                m.forEach { append("\nQ: ").append(it.entry.question.take(200)).append("\nA: ").append(it.entry.answer.take(500)) }
            }
        } else ""
        val sys = listOf(systemPrompt(), hint, deep, memory).filter { it.isNotEmpty() }.joinToString("\n")
        val req = RouteRequest(
            messages = listOf(ChatMsg("system", sys)) + history,
            mode = mode,
            pinnedKey = if (mode == ChatMode.PINNED) sel.removePrefix("PIN:") else null,
            candidates = cands,
            preferredKey = preferred,
            maxTokens = if (saver && kind == TaskKind.QUICK && cont == null) QUICK_MAX_TOKENS else null,
            adaptive = adaptive,
            preferSpeed = adaptive && kind == TaskKind.QUICK,
            needsVision = needsVision
        )

        val text = StringBuilder(prefix)
        val notes = mutableListOf<String>()
        if (adaptive) {
            val label = app.getString(
                when (kind) {
                    TaskKind.QUICK -> R.string.kind_quick
                    TaskKind.CODE -> R.string.kind_code
                    TaskKind.REASON -> R.string.kind_reason
                    TaskKind.WRITE -> R.string.kind_write
                    TaskKind.GENERAL -> R.string.kind_general
                }
            )
            var note = app.getString(R.string.adaptive_note, label)
            if (saved >= 40) note += " · " + app.getString(R.string.tokens_saved, saved)
            notes.add(note)
        }
        if (needsVision) notes.add(app.getString(R.string.vision_note))
        val hold = StringBuilder()
        var released = cont == null
        val tail = prefix.takeLast(240)
        var target: Target? = null
        var error: String? = null
        var pinnedFailure = false
        var lastPush = 0L

        fun push(force: Boolean = false) {
            val n = System.currentTimeMillis()
            if (force || n - lastPush >= 50) {
                lastPush = n
                stream.value = StreamUi(text.toString(), notes.toList(), target, true, continuingId = cont?.id)
            }
        }

        stream.value = StreamUi(text.toString(), active = true, continuingId = cont?.id)
        try {
            router.stream(req).collect { ev ->
                when (ev) {
                    is RouteEvent.Started -> {
                        target = ev.target
                        push(true)
                    }
                    is RouteEvent.Delta -> {
                        if (released) {
                            text.append(ev.text)
                        } else {
                            hold.append(ev.text)
                            if (hold.length >= 48) {
                                released = true
                                val rest = Overlap.strip(tail, hold.toString())
                                text.append(joinSep(prefix, rest)).append(rest)
                                hold.setLength(0)
                            }
                        }
                        push()
                    }
                    is RouteEvent.Switched -> {
                        target = ev.target
                        notes.add(app.getString(R.string.switched_to, ev.target.model.displayName))
                        push(true)
                    }
                    is RouteEvent.Restart -> {
                        text.setLength(0)
                        text.append(prefix)
                        push(true)
                    }
                    is RouteEvent.AuthFailed -> {
                        val name = all.firstOrNull { it.providerId == ev.providerId }?.providerName ?: ev.providerId
                        if ('#' !in ev.providerId) manager.markFailed(ev.providerId)
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
                if (!released && hold.isNotEmpty()) {
                    val rest = Overlap.strip(tail, hold.toString())
                    text.append(joinSep(prefix, rest)).append(rest)
                }
                val finalText = text.toString()
                val t = target
                if (cont != null) {
                    if (finalText.length > prefix.length && t != null) {
                        val changed = cont.modelKey != t.key
                        val allNotes = buildList {
                            cont.notes?.let { add(it) }
                            addAll(notes)
                            if (changed) add(app.getString(R.string.continued_by, t.model.displayName))
                        }.joinToString("\n").ifEmpty { null }
                        repo.updateAssistant(
                            id = cont.id,
                            content = finalText,
                            modelName = if (changed) "${cont.modelName.orEmpty()} → ${t.model.displayName}" else cont.modelName,
                            providerName = t.providerName,
                            modelKey = t.key,
                            notes = allNotes
                        )
                    }
                } else if (finalText.isNotBlank()) {
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
                if (error != null && finalText.isBlank() && cont == null && !hasAtt) {
                    val fb = brain.best(lastUser, LOCAL_FALLBACK)
                    if (fb != null) {
                        brain.markUsed(fb.entry.id)
                        repo.addMessage(
                            MessageEntity(
                                conversationId = cid,
                                role = "assistant",
                                content = fb.entry.answer,
                                modelName = app.getString(R.string.local_brain_name),
                                providerName = app.getString(R.string.local_brain_provider),
                                modelKey = null,
                                notes = app.getString(R.string.local_fallback_note, (fb.similarity * 100).toInt()),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        error = null
                    }
                } else if (error == null && finalText.isNotBlank() && t != null && !hasAtt &&
                    finalText.length > prefix.length && app.settings.brainLearn.first()
                ) {
                    if (brain.learn(lastUser, finalText) != null) app.brainStore.save()
                }
                val err = error
                lastError = err
                lastPinned = pinnedFailure
                stream.value = if (err != null) StreamUi(error = err, pinnedFailure = pinnedFailure) else null
            }
        }
    }

    companion object {
        private const val MAX_PENDING = 6
        private const val MAX_HISTORY_IMAGES = 4
        private const val MAX_RESUME = 2
        private const val RESUME_DELAY_MS = 15_000L
        private const val QUICK_MAX_TOKENS = 1200
        private const val LOCAL_CONFIDENT = 0.86
        private const val LOCAL_FALLBACK = 0.40
        private const val DEEP_THINK_PROMPT =
            "Work through the problem carefully first, check your result for mistakes, then give a clear final answer."
        private const val SYSTEM_PROMPT =
            "You are a helpful assistant. Reply in the same language as the user's last message."
        private const val CONTINUE_PROMPT =
            "Continue your previous reply exactly where it stopped. Do not repeat anything and do not add an introduction. " +
                "أكمل ردك السابق من حيث توقفت تماماً دون تكرار ودون مقدمة."
    }
}
