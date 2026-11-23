@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.freellm.chat.ui

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import com.freellm.chat.domain.BrainPolicy
import com.freellm.chat.domain.HistoryImages
import com.freellm.chat.domain.ResumePolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freellm.chat.FreeLlmApp
import android.net.Uri
import com.freellm.chat.R
import com.freellm.chat.data.Attachment
import com.freellm.chat.data.AttachmentCodec
import com.freellm.chat.data.PdfExport
import com.freellm.chat.data.db.AnswerCacheEntity
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.AmbiguityResolver
import com.freellm.chat.domain.CacheKey
import com.freellm.chat.domain.Difficulty
import kotlinx.coroutines.async
import com.freellm.chat.domain.QuestionClassifier
import com.freellm.chat.domain.CachePolicy
import com.freellm.chat.domain.CompareChoice
import com.freellm.chat.domain.CompareRequest
import com.freellm.chat.domain.CompareResult
import com.freellm.chat.domain.CompareSide
import com.freellm.chat.domain.Merger
import com.freellm.chat.domain.AnswerPolicy
import com.freellm.chat.domain.QueryBuilder
import com.freellm.chat.domain.TemperaturePolicy
import com.freellm.chat.domain.Verifier
import com.freellm.chat.domain.SearchBundle
import com.freellm.chat.domain.SearchLimits
import com.freellm.chat.domain.SearchPolicy
import com.freellm.chat.domain.SourcesPrompt
import com.freellm.chat.domain.StoredSource
import com.freellm.chat.data.SearchFactory
import com.freellm.chat.domain.SideState
import com.freellm.chat.domain.ChatMode
import com.freellm.chat.domain.ContextPlanner
import com.freellm.chat.domain.DocChunker
import com.freellm.chat.domain.LocalBrain
import com.freellm.chat.domain.MaxTokensPlanner
import com.freellm.chat.domain.OutputQuality
import com.freellm.chat.domain.RequestFitter
import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.RouteEvent
import com.freellm.chat.domain.Overlap
import com.freellm.chat.domain.RouteRequest
import com.freellm.chat.domain.TaskClassifier
import com.freellm.chat.domain.TaskKind
import com.freellm.chat.domain.TokenSaver
import com.freellm.chat.domain.ToolCatalog
import com.freellm.chat.domain.ToolContext
import com.freellm.chat.domain.ToolPolicy
import com.freellm.chat.domain.ToolProtocol
import com.freellm.chat.domain.ToolSpec
import com.freellm.chat.domain.ToolStep
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

data class MergeUi(val busy: Boolean = false, val text: String? = null, val failed: Boolean = false)

data class CompareUi(
    val cid: Long,
    val userId: Long,
    val question: String,
    val result: CompareResult,
    val merge: MergeUi? = null
)

data class StreamUi(
    val text: String = "",
    val notes: List<String> = emptyList(),
    val target: Target? = null,
    val active: Boolean = false,
    val error: String? = null,
    val pinnedFailure: Boolean = false,
    /** Set when a model is continuing an existing assistant message (hand-off). */
    val continuingId: Long? = null,
    val status: String? = null,
    val sources: List<StoredSource> = emptyList(),
    val progress: Float? = null
)

class FetchAsk(val host: String, val answer: kotlinx.coroutines.CompletableDeferred<Boolean>)

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
    val fetchAsk = MutableStateFlow<FetchAsk?>(null)
    val compareUi = MutableStateFlow<CompareUi?>(null)
    private var job: Job? = null
    @Volatile private var activeCid: Long? = null
    @Volatile private var lastNoResume = false
    private var compareJob: Job? = null
    private var mergeJob: Job? = null
    private var lastError: String? = null
    private var lastPinned = false
    private var skipLocalOnce = false
    private val brain get() = app.brainStore.brain

    val conversations: StateFlow<List<ConversationEntity>> = query
        .debounce(300)
        .flatMapLatest { repo.conversations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ConversationEntity>())

    val messages: StateFlow<List<MessageEntity>> = conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<MessageEntity>()) else repo.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MessageEntity>())

    val targets: StateFlow<List<Target>> = manager.providers
        .map { manager.activeTargets().filter { t -> '#' !in t.providerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Target>())

    fun answerFetch(ok: Boolean) {
        fetchAsk.value?.answer?.complete(ok)
    }

    init {
        app.toolHost.confirmFetch = { host ->
            val ask = FetchAsk(host, kotlinx.coroutines.CompletableDeferred())
            fetchAsk.value = ask
            try {
                ask.answer.await()
            } finally {
                fetchAsk.value = null
            }
        }
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val saved = app.settings.selection.first()
            selection.value = if (saved.startsWith("PIN:")) "ADAPT" else saved
            sweepAttachments()
        }
    }

    val generating: Boolean get() = job?.isActive == true

    fun latency(t: Target): Long? = router.stats.latencyMs(t.key)

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
        app.warmUp()
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            job?.cancelAndJoin()
            dismissCompare()
            stream.value = null
            conversationId.value = id
            val c = repo.conversation(id)
            selection.value = c?.pinnedModel?.let { "PIN:$it" } ?: nonPin()
        }
    }

    fun newChat() {
        job?.cancel()
        dismissCompare()
        stream.value = null
        conversationId.value = null
        selection.value = nonPin()
    }

    fun select(sel: String) {
        selection.value = sel
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            if (!sel.startsWith("PIN:")) app.settings.setSelection(sel)
            conversationId.value?.let { repo.setPinned(it, sel.removePrefix("PIN:").takeIf { _ -> sel.startsWith("PIN:") }) }
        }
    }

    fun warm() {
        app.warmUp()
    }

    fun stop() {
        job?.cancel()
        val s = stream.value
        if (s != null && s.error == null) stream.value = s.copy(active = false, status = null, progress = null)
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
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
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
        m.modelKey?.let { app.ratings.rate(it, good) }
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val list = repo.messagesOnce(m.conversationId)
            val idx = list.indexOfFirst { it.id == m.id }
            val q = list.take(if (idx < 0) list.size else idx).lastOrNull { it.role == "user" }?.content ?: return@launch
            if (QuestionClassifier.arabicRatio(q) > 0.5) m.modelKey?.let { app.ratings.rateArabic(it, good) }
            val qPos = list.take(if (idx < 0) list.size else idx).indexOfLast { it.role == "user" }
            val learnable = BrainPolicy.canLearn(q, m.content, qPos + 1, QuestionClassifier.needsFresh(q))
            if (good) {
                if (learnable) brain.learn(q, m.content)?.let { brain.feedback(it.id, true) }
                notice.value = app.getString(R.string.brain_thanks)
            } else {
                brain.forgetQuestion(q)
                repo.cacheForget(LocalBrain.normalize(q))
                notice.value = app.getString(R.string.brain_forgot)
            }
            app.brainStore.save()
        }
    }

    fun toggleStar(c: ConversationEntity) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) { repo.setStarred(c.id, !c.starred) }
    }

    fun setConversationInstructions(id: Long, text: String) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) { repo.setInstructions(id, text) }
    }

    fun rename(id: Long, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) { repo.rename(id, t) }
    }

    fun delete(id: Long) {
        if (conversationId.value == id) newChat()
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            if (activeCid == id) job?.cancelAndJoin()
            repo.deleteConversation(id)
            withContext(kotlinx.coroutines.Dispatchers.IO) { app.workspace.clear(id) }
            sweepAttachments()
        }
    }

    private suspend fun sweepAttachments() {
        val refs = HashSet<String>()
        repo.attachmentRefs().forEach { raw -> AttachmentCodec.decode(raw).forEach { refs.add(it.path) } }
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!generating) app.attachments.sweep(refs, pending.value.map { it.path }.toSet(), SWEEP_MIN_AGE_MS)
        }
    }

    fun export(id: Long, onReady: (String) -> Unit) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) { onReady(repo.exportMarkdown(id)) }
    }

    fun deleteMessage(m: MessageEntity) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            repo.deleteMessage(m.id)
            sweepAttachments()
        }
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

    fun compare(sel1: String, sel2: String) {
        val cid = conversationId.value ?: return
        if (generating) return
        val k1 = sel1.removePrefix("PIN:")
        val k2 = sel2.removePrefix("PIN:")
        if (k1 == k2) {
            notice.value = app.getString(R.string.compare_need_two)
            return
        }
        compareJob?.cancel()
        mergeJob?.cancel()
        compareJob = viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { manager.activeTargets() }
            val a = all.firstOrNull { it.key == k1 } ?: return@launch
            val b = all.firstOrNull { it.key == k2 } ?: return@launch
            val stored = repo.messagesOnce(cid).filter { it.role == "user" || it.role == "assistant" }
            val lastIdx = stored.indexOfLast { it.role == "user" }
            if (lastIdx < 0) {
                notice.value = app.getString(R.string.compare_need_question)
                return@launch
            }
            val upTo = stored.subList(0, lastIdx + 1)
            val asked = upTo.last()
            val (_, recent) = ContextPlanner.split(upTo, ContextPlanner.KEEP_RECENT) { it.role }
            val hist = toChat(recent, asked.id, app.settings.smartAttachments.first()).map { it.copy(images = emptyList()) }
            val hasAtt = AttachmentCodec.decode(asked.attachments).isNotEmpty()
            val kind = TaskClassifier.classify(asked.content)
            val maxTok = if (app.settings.dynamicTokens.first()) {
                MaxTokensPlanner.plan(kind, asked.content, app.settings.style.first(), hasAtt)
            } else null
            val msgs = listOf(ChatMsg("system", systemPrompt(cid))) + hist
            compareUi.value = CompareUi(cid, asked.id, asked.content, CompareResult(CompareSide(a), CompareSide(b)))
            app.comparer.run(CompareRequest(asked.content, a, b, msgs, maxTok)).collect { r ->
                compareUi.value = compareUi.value?.copy(result = r)
            }
        }
    }

    fun choose(first: Boolean) {
        val ui = compareUi.value ?: return
        val side = ui.result.side(first)
        if (side.state != SideState.DONE || side.text.isBlank()) return
        val arabic = QuestionClassifier.arabicRatio(ui.question) > 0.5
        CompareChoice.apply(ui.result, first, arabic, { k, g -> app.ratings.rate(k, g) }, { k, g -> app.ratings.rateArabic(k, g) })
        dismissCompare()
        saveChosen(ui, side.text, side.target.model.displayName, side.target.providerName, side.target.key)
    }

    fun merge() {
        val ui = compareUi.value ?: return
        if (!Merger.canMerge(ui.result) || ui.merge?.busy == true) return
        compareUi.value = ui.copy(merge = MergeUi(busy = true))
        mergeJob?.cancel()
        mergeJob = viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val out = Merger.run(app.sideTasks, manager.activeTargets(), ui.result, ui.question)
            compareUi.value = compareUi.value?.copy(merge = MergeUi(text = out, failed = out == null))
        }
    }

    fun chooseMerged() {
        val ui = compareUi.value ?: return
        val text = ui.merge?.text?.takeIf { it.isNotBlank() } ?: return
        dismissCompare()
        saveChosen(ui, text, app.getString(R.string.compare_merged_name), app.getString(R.string.compare_merged_provider), null)
    }

    fun dismissCompare() {
        compareJob?.cancel()
        mergeJob?.cancel()
        compareUi.value = null
    }

    private fun saveChosen(ui: CompareUi, text: String, modelName: String, providerName: String, modelKey: String?) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val after = repo.messagesOnce(ui.cid).firstOrNull { it.id > ui.userId }
            if (after != null) repo.deleteFrom(ui.cid, after.id)
            repo.addMessage(
                MessageEntity(
                    conversationId = ui.cid,
                    role = "assistant",
                    content = text,
                    modelName = modelName,
                    providerName = providerName,
                    modelKey = modelKey,
                    notes = app.getString(R.string.compare_chosen_note),
                    createdAt = System.currentTimeMillis()
                )
            )
            brain.learn(ui.question, text)?.let { brain.feedback(it.id, true) }
            app.brainStore.save()
            notice.value = app.getString(R.string.compare_saved)
        }
    }

    fun deleteAllChats() {
        job?.cancel()
        stream.value = null
        conversationId.value = null
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            job?.cancelAndJoin()
            repo.deleteAll()
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.workspace.clearAll()
                app.attachments.sweep(emptySet(), pending.value.map { it.path }.toSet(), SWEEP_MIN_AGE_MS)
            }
        }
    }

    private fun addPending(r: com.freellm.chat.data.ImportResult) {
        if (r.items.isNotEmpty()) pending.value = (pending.value + r.items).takeLast(MAX_PENDING)
        r.notice?.let { notice.value = it }
    }

    fun attachImages(uris: List<Uri>) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
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
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
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
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
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
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
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

    fun saveCard(uri: Uri, m: MessageEntity, rtl: Boolean, dark: Boolean, done: (Boolean) -> Unit) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val srcs = com.freellm.chat.domain.SourcesPrompt.decode(m.sources).map {
                        it.title.ifBlank { com.freellm.chat.data.CardText.host(it.url) }
                    }
                    val bmp = com.freellm.chat.data.ShareCard.render(
                        m.content, srcs, app.getString(R.string.share_card_app),
                        app.getString(R.string.share_card_sources), rtl, dark
                    )
                    val out = app.contentResolver.openOutputStream(uri) ?: return@withContext false
                    out.use { com.freellm.chat.data.ShareCard.write(bmp, it) }
                } catch (e: Throwable) {
                    false
                }
            }
            if (!ok) notice.value = app.getString(R.string.share_card_failed)
            done(ok)
        }
    }

    fun saveFile(uri: Uri, m: MessageEntity, done: (Boolean) -> Unit) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val f = com.freellm.chat.domain.CodeFiles.extractFinal(m.content).firstOrNull() ?: return@withContext false
                    val out = app.contentResolver.openOutputStream(uri) ?: return@withContext false
                    out.use { it.write(f.content.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Throwable) {
                    false
                }
            }
            notice.value = app.getString(if (ok) R.string.file_saved else R.string.file_save_failed)
            done(ok)
        }
    }

    fun saveZip(uri: Uri, m: MessageEntity, done: (Boolean) -> Unit) {
        viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            var count = 0
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val extracted = com.freellm.chat.domain.CodeFiles.extractFinal(m.content)
                    val files = if (m.notes?.contains("📁") == true) {
                        (app.workspace.files(m.conversationId) + extracted).distinctBy { it.path }
                    } else extracted
                    if (files.isEmpty()) return@withContext false
                    val out = app.contentResolver.openOutputStream(uri) ?: return@withContext false
                    count = out.use { com.freellm.chat.data.ZipExport.write(files, it) }
                    true
                } catch (e: Throwable) {
                    false
                }
            }
            notice.value = if (ok) app.getString(R.string.zip_saved, count) else app.getString(R.string.zip_failed)
            done(ok)
        }
    }

    private suspend fun toChat(
        list: List<MessageEntity>,
        lastUserId: Long?,
        smart: Boolean,
        onImage: (Int, Int) -> Unit = { _, _ -> }
    ): List<ChatMsg> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            var budget = MAX_HISTORY_IMAGES
            val imageTotal = minOf(MAX_HISTORY_IMAGES, list.sumOf { m ->
                if (m.role == "user") AttachmentCodec.decode(m.attachments).count { it.isImage } else 0
            })
            var imageDone = 0
            list.asReversed().map { m ->
                val atts = AttachmentCodec.decode(m.attachments)
                if (m.role != "user" || atts.isEmpty()) {
                    ChatMsg(m.role, if (m.role == "assistant") com.freellm.chat.domain.ThinkSplit.strip(m.content) else m.content)
                } else {
                    val imgs = ArrayList<String>()
                    for (a in atts.filter { it.isImage }.asReversed()) {
                        if (budget > 0) {
                            app.attachments.dataUrl(a)?.let {
                                imgs.add(0, it)
                                budget--
                            }
                            imageDone++
                            onImage(imageDone, imageTotal)
                        }
                    }
                    val sb = StringBuilder()
                    atts.filter { !it.isImage }.forEach { f ->
                        val raw = app.attachments.readText(f)
                        val body = if (smart) {
                            DocChunker.select(raw, m.content, if (m.id == lastUserId) FILE_BUDGET_LAST else FILE_BUDGET_OLD)
                        } else raw
                        sb.append("[File: ").append(f.name).append("]\n")
                            .append(body).append("\n[End of file]\n\n")
                    }
                    sb.append(m.content)
                    ChatMsg("user", sb.toString(), imgs)
                }
            }.asReversed()
        }

    fun send(text: String): Boolean {
        val t0 = text.trim()
        val atts = pending.value
        if ((t0.isEmpty() && atts.isEmpty()) || generating || importing.value) return false
        val t = if (t0.isEmpty()) {
            app.getString(if (atts.any { it.isImage }) R.string.default_prompt_image else R.string.default_prompt_file)
        } else t0
        pending.value = emptyList()
        app.warmUp()
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
        return true
    }

    private fun start(block: suspend () -> Unit) {
        val prev = job
        job = viewModelScope.launch(com.freellm.chat.data.CrashLog.guard) {
            prev?.cancelAndJoin()
            block()
        }
    }

    private suspend fun systemPrompt(cid: Long, codeFiles: Boolean = true, g: com.freellm.chat.data.GenSettings? = null, lang: String? = null): String {
        val style = when (g?.style ?: app.settings.style.first()) {
            "concise" -> "Be brief."
            "detailed" -> "Be thorough and structured."
            "teacher" -> "Teach step by step with simple examples."
            else -> ""
        }
        val vars = { t: String ->
            com.freellm.chat.domain.PromptVars.expand(
                t, java.time.LocalDateTime.now(), android.os.Build.MODEL.orEmpty(),
                java.util.Locale.getDefault().displayLanguage
            )
        }
        val custom = vars((g?.instructions ?: app.settings.instructions.first()).trim())
        val facts = (g?.facts ?: app.settings.facts.first()).trim().take(1500)
        val convo = vars(repo.conversation(cid)?.instructions.orEmpty().trim())
        return listOf(
            SYSTEM_PROMPT,
            com.freellm.chat.domain.LangGuard.instruction(lang),
            if (codeFiles) com.freellm.chat.domain.CodeFiles.SYSTEM else "",
            style,
            if (facts.isNotEmpty()) "User facts (use naturally): $facts" else "",
            if (custom.isNotEmpty()) "User rules: $custom" else "",
            if (convo.isNotEmpty()) "Chat rules (priority): $convo" else ""
        ).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private val summarizing = HashSet<Long>()

    private fun maybeSummarize(cid: Long) {
        synchronized(summarizing) { if (!summarizing.add(cid)) return }
        app.appScope.launch {
            try {
                delay(SUMMARY_IDLE_MS)
                var idle = 0
                while (generating && idle < 60) {
                    delay(1000)
                    idle++
                }
                val msgs = repo.messagesOnce(cid).filter { it.role == "user" || it.role == "assistant" }
                val conv = repo.conversation(cid) ?: return@launch
                val (older, _) = ContextPlanner.split(msgs, ContextPlanner.KEEP_RECENT) { it.role }
                val pending = older.filter { it.id > conv.summarizedUpTo }
                if (!ContextPlanner.shouldUpdate(pending.size, pending.sumOf { it.content.length })) return@launch
                val out = app.sideTasks.run(
                    manager.activeTargets(),
                    ContextPlanner.SUMMARY_SYSTEM,
                    ContextPlanner.summaryPrompt(conv.summary, pending.map { ChatMsg(it.role, com.freellm.chat.domain.ThinkSplit.strip(it.content)) }),
                    260
                ) ?: return@launch
                repo.setSummary(cid, ContextPlanner.clean(out), pending.last().id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
            } finally {
                synchronized(summarizing) { summarizing.remove(cid) }
            }
        }
    }

    private fun joinSep(prefix: String, next: String): String {
        val a = prefix.lastOrNull() ?: return ""
        val b = next.firstOrNull() ?: return ""
        if (a.isWhitespace() || b.isWhitespace()) return ""
        if (b in ".,;:!?)]}\u060C\u061B\u061F") return ""
        return " "
    }

    private suspend fun runAdaptive(cid: Long, exclude: String?, cont: MessageEntity? = null) {
        lastError = null
        lastNoResume = false
        activeCid = cid
        try {
            runAdaptiveInner(cid, exclude, cont)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            stream.value = StreamUi(error = lastError)
        } finally {
            activeCid = null
            if (stream.value?.active == true) stream.value = null
        }
    }

    private suspend fun runAdaptiveInner(cid: Long, exclude: String?, cont: MessageEntity? = null) {
        generate(cid, exclude, cont)
        autoComplete(cid)
        if (selection.value != "ADAPT" || !app.settings.autoResume.first()) return
        var tries = 0
        try {
            while (lastError != null && !lastPinned && !lastNoResume && tries < MAX_RESUME) {
                if (conversationId.value != cid) break
                tries++
                delay(RESUME_DELAY_MS * tries)
                if (conversationId.value != cid) break
                val last = repo.messagesOnce(cid).lastOrNull { it.role == "user" || it.role == "assistant" }
                if (last == null || last.role != "assistant" || last.content.isBlank()) {
                    stream.value = StreamUi(error = lastError, pinnedFailure = lastPinned)
                    break
                }
                notice.value = app.getString(R.string.auto_resume_notice, tries)
                stream.value = StreamUi(active = true)
                lastError = null
                lastNoResume = false
                generate(cid, null, last)
            }
        } catch (e: CancellationException) {
            stream.value = null
            throw e
        }
    }

    private suspend fun autoComplete(cid: Long) {
        if (!app.settings.projectAutoFix.first()) return
        var tries = 0
        while (tries < MAX_REPAIR && lastError == null && conversationId.value == cid) {
            val list = repo.messagesOnce(cid).filter { it.role == "user" || it.role == "assistant" }
            val last = list.lastOrNull() ?: return
            if (last.role != "assistant" || last.notes?.contains("📁") == true) return
            val ask = list.lastOrNull { it.role == "user" }?.content ?: return
            if (!(com.freellm.chat.domain.CodeFiles.wantsProject(ask) || com.freellm.chat.domain.CodeFiles.wantsZip(ask))) return
            val review = com.freellm.chat.domain.ProjectCheck.review(last.content)
            if (review.files.isEmpty() || review.issues.isEmpty()) return
            tries++
            notice.value = app.getString(R.string.project_fixing, tries)
            stream.value = StreamUi(active = true, continuingId = last.id)
            generate(cid, null, last, com.freellm.chat.domain.ProjectCheck.fixPrompt(review.issues))
        }
    }

    fun repairProject() {
        val cid = conversationId.value ?: return
        start {
            val last = repo.messagesOnce(cid).lastOrNull { it.role == "assistant" } ?: return@start
            val review = com.freellm.chat.domain.ProjectCheck.review(last.content)
            val prompt = if (review.issues.isEmpty()) com.freellm.chat.domain.CodeFiles.REPAIR_CONTINUE else com.freellm.chat.domain.ProjectCheck.fixPrompt(review.issues)
            stream.value = StreamUi(active = true, continuingId = last.id)
            runRepair(cid, last, prompt)
        }
    }

    private suspend fun runRepair(cid: Long, last: MessageEntity, prompt: String) {
        lastError = null
        lastNoResume = false
        activeCid = cid
        try {
            generate(cid, null, last, prompt)
        } finally {
            activeCid = null
            if (stream.value?.active == true) stream.value = null
        }
    }

    private suspend fun generate(cid: Long, exclude: String?, cont: MessageEntity? = null, repair: String? = null) {
        val t0 = System.currentTimeMillis()
        val gJob = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { app.settings.snapshot() }
        val storedJob = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { repo.messagesOnce(cid) }
        val convJob = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { repo.conversation(cid) }
        val targetsJob = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { manager.activeTargets() }
        val g = gJob.await()
        val stored = storedJob.await()
            .filter { it.role == "user" || it.role == "assistant" }
            .let { l -> if (cont != null) l.filter { it.id <= cont.id } else l }
        val conv = convJob.await()
        val summaryOn = g.contextSummary
        var windowed = stored
        var summaryLine = ""
        if (summaryOn) {
            val (older, recent) = ContextPlanner.split(stored, ContextPlanner.KEEP_RECENT) { it.role }
            if (older.isNotEmpty()) {
                windowed = recent
                val have = conv?.summary.orEmpty()
                val upTo = conv?.summarizedUpTo ?: 0L
                val pending = older.filter { it.id > upTo }
                val extra = if (pending.isEmpty() && have.isNotEmpty()) "" else ContextPlanner.digest(pending.map { ChatMsg(it.role, com.freellm.chat.domain.ThinkSplit.strip(it.content)) })
                summaryLine = listOf(have, extra).filter { it.isNotEmpty() }.joinToString("\n")
            }
        }
        val lastUserId = stored.lastOrNull { it.role == "user" }?.id
        val lastImageCount = stored.lastOrNull { it.role == "user" }?.let { AttachmentCodec.decode(it.attachments).count { a -> a.isImage } } ?: 0
        if (lastImageCount > 0) {
            stream.value = StreamUi(
                active = true, continuingId = cont?.id,
                status = app.getString(R.string.image_prep, 0, lastImageCount), progress = IMAGE_PREP_START
            )
        }
        var history = toChat(windowed, lastUserId, g.smartAttachments) { done, total ->
            if (total > 0) {
                stream.value = StreamUi(
                    active = true, continuingId = cont?.id,
                    status = app.getString(R.string.image_prep, done, total),
                    progress = IMAGE_PREP_START + (IMAGE_PREP_END - IMAGE_PREP_START) * done / total
                )
            }
        }.toMutableList()
        val prefix = cont?.content.orEmpty()
        if (cont != null) history.add(ChatMsg("user", repair ?: CONTINUE_PROMPT))

        val all = manager.activeTargets()
        val sel = selection.value
        val adaptive = sel == "ADAPT"
        val saver = g.tokenSaver
        val lastUser = stored.lastOrNull { it.role == "user" }?.content.orEmpty()
        val userTurns = stored.filter { it.role == "user" }
        val prevUser = if (userTurns.size > 1) userTurns[userTurns.size - 2].content else ""
        val followUp = stored.size > 1
        val shortFollowUp = followUp && lastUser.length <= FOLLOW_UP_CHARS
        val skipMemory = BrainPolicy.skipMemory(lastUser, stored.size)
        val ctxText = if (shortFollowUp && prevUser.isNotEmpty()) prevUser.take(400) + "\n" + lastUser else lastUser
        val lastAssistantLen = stored.lastOrNull { it.role == "assistant" }?.content?.length ?: 0
        val rawKind = TaskClassifier.classify(ctxText)
        val kind = if (rawKind == TaskKind.QUICK && shortFollowUp && lastAssistantLen > 500) TaskKind.GENERAL else rawKind
        val lastUserMsg = stored.lastOrNull { it.role == "user" }
        val hasAtt = lastUserMsg?.let { AttachmentCodec.decode(it.attachments).isNotEmpty() } == true
        val wsJob = if (cont == null && g.toolsEnabled && !ToolPolicy.trivial(lastUser)) {
            viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { app.workspace.count(cid) > 0 }
        } else null
        val memoryJob = if (cont == null && !hasAtt && !shortFollowUp && g.brainContext) {
            viewModelScope.async(kotlinx.coroutines.Dispatchers.Default) { brain.search(lastUser, 2, 0.5) }
        } else null
        val skipLocal = skipLocalOnce
        skipLocalOnce = false
        val smart = g.smartRouting
        var cls = QuestionClassifier.classify(ctxText, hasAtt)
        if (smart && adaptive && cls.ambiguous && cont == null && exclude == null && !followUp) {
            cls = cls.copy(type = AmbiguityResolver.heuristic(lastUser), ambiguous = false)
        }
        val msgLang = com.freellm.chat.domain.LangGuard.detect(lastUser)
        val guardLang = if (cont != null || com.freellm.chat.domain.LangGuard.translationIntent(lastUser)) null else msgLang
        val tClass = System.currentTimeMillis()
        val cacheTtl = CachePolicy.ttlMs(g.cacheTtlHours)
        val cacheKey = if (g.answerCache && cont == null && !hasAtt && !sel.startsWith("PIN:") &&
            stored.lastOrNull()?.role == "user" && CacheKey.cacheable(lastUser) && !skipMemory && !cls.needsFresh &&
            !com.freellm.chat.domain.LangGuard.isGreeting(lastUser)
        ) CacheKey.of(
            lastUser, CacheKey.contextOf(stored.dropLast(1).map { it.content }),
            g.style, g.instructions, msgLang.orEmpty()
        ) else null
        val cacheJob = if (cacheKey != null && exclude == null && !skipLocal) {
            viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { repo.cacheGet(cacheKey, cacheTtl, System.currentTimeMillis()) }
        } else null
        val localJob = if (cont == null && exclude == null && !skipLocal && !hasAtt && !skipMemory && !cls.needsFresh && !LocalBrain.isTimeSensitive(lastUser) &&
            stored.lastOrNull()?.role == "user" && g.brainLocalFirst
        ) {
            viewModelScope.async(kotlinx.coroutines.Dispatchers.Default) { brain.bestStrict(lastUser, LOCAL_CONFIDENT) }
        } else null
        if (cacheJob != null) {
            val hit = cacheJob.await()
            if (hit != null) {
                localJob?.cancel()
                memoryJob?.cancel()
                wsJob?.cancel()
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = hit.answer,
                        modelName = hit.modelName,
                        providerName = hit.providerName,
                        modelKey = null,
                        notes = app.getString(R.string.cache_note),
                        createdAt = System.currentTimeMillis()
                    )
                )
                lastError = null
                lastPinned = false
                stream.value = null
                return
            }
        }
        if (localJob != null) {
            val hit = localJob.await()
            if (hit != null) {
                memoryJob?.cancel()
                wsJob?.cancel()
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
        var bundle: SearchBundle? = null
        var searchNote: String? = null
        val tSearch0 = System.currentTimeMillis()
        var lateSearch: kotlinx.coroutines.Deferred<com.freellm.chat.domain.SearchOutcome?>? = null
        var mandatorySearch: kotlinx.coroutines.Deferred<com.freellm.chat.domain.SearchOutcome>? = null
        val useLocal = g.localSearch
        val searchOn = SearchPolicy.resolveEnabled(g.webSearch, useLocal || SearchFactory.anyConfigured(app.secrets))
        if (searchOn) {
            val keyed = SearchFactory.resolve(
                com.freellm.chat.domain.SearchKind.of(g.searchProvider), app.searchHttp, app.secrets, g.proxyUrl
            )
            val keyedLeft = keyed?.let {
                app.searchQuota.remaining(it.kind, SearchLimits.effective(it.kind, g.searchLimits), System.currentTimeMillis())
            } ?: 0
            val prov: com.freellm.chat.domain.SearchProvider? = when {
                keyed != null && keyedLeft > 0 ->
                    if (useLocal) com.freellm.chat.domain.ChainSearch(keyed, app.localSearch) else keyed
                useLocal -> app.localSearch
                else -> keyed
            }
            if (prov != null) {
                val limit = SearchLimits.effective(prov.kind, g.searchLimits)
                val left = app.searchQuota.remaining(prov.kind, limit, System.currentTimeMillis())
                val mandatoryNow = SearchPolicy.shouldSearch(true, true, cls.needsFresh, false, hasAtt, cont != null, left)
                val rewriteAssist = !com.freellm.chat.domain.QueryBuilder.skipRewrite(lastUser, hasAtt, mandatoryNow)
                if (SearchPolicy.shouldAssist(true, true, cls.needsFresh, lastUser, hasAtt, cont != null, left, limit.amount)) {
                    lateSearch = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(LATE_SEARCH_MS) {
                            app.webSearch.run(
                                prov, limit, lastUser, all, QueryBuilder.isTechnical(lastUser, cls.type),
                                CachePolicy.ttlMs(g.searchCacheHours),
                                maxQueries = if (prov.kind == com.freellm.chat.domain.SearchKind.LOCAL) 2 else QueryBuilder.MAX_QUERIES,
                                rewrite = rewriteAssist
                            )
                        }
                    }
                }
                if (mandatoryNow) {
                    stream.value = StreamUi(active = true, status = app.getString(R.string.searching_web))
                    mandatorySearch = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) {
                        app.webSearch.run(
                            prov, limit, lastUser, all, QueryBuilder.isTechnical(lastUser, cls.type),
                            CachePolicy.ttlMs(g.searchCacheHours),
                            maxQueries = if (prov.kind == com.freellm.chat.domain.SearchKind.LOCAL) 2 else QueryBuilder.MAX_QUERIES
                        )
                    }
                }
            }
        }
        var saved = 0
        if (saver) {
            val c = TokenSaver.compact(history, if (followUp && kind == TaskKind.QUICK) TaskKind.GENERAL else kind)
            history = c.messages.toMutableList()
            saved = c.savedTokens
        }
        val lastHasImages = lastUserMsg?.let { AttachmentCodec.decode(it.attachments).any { a -> a.isImage } } == true
        val needsVision = lastHasImages && history.any { it.images.isNotEmpty() }
        var mode = modeOf(sel)
        if (adaptive) {
            mode = if (!smart) kind.mode else when {
                cls.type == com.freellm.chat.domain.QType.CODE -> ChatMode.CODE
                cls.difficulty == Difficulty.HARD -> ChatMode.SMART
                cls.difficulty == Difficulty.EASY -> ChatMode.FAST
                kind == TaskKind.WRITE -> ChatMode.SMART
                else -> kind.mode
            }
        }
        if (needsVision && mode == ChatMode.PINNED &&
            all.firstOrNull { it.key == sel.removePrefix("PIN:") }?.model?.vision != true
        ) mode = ChatMode.AUTO
        var cands = all
        var preferred: String? = null
        val lastModelKey = if (exclude == null) stored.lastOrNull { it.role == "assistant" && it.modelKey != null }?.modelKey else null
        if (exclude != null) {
            if (mode == ChatMode.PINNED) mode = ChatMode.AUTO
            val filtered = all.filter { it.key != exclude }
            if (filtered.isNotEmpty()) cands = filtered
        } else if (mode == ChatMode.PINNED && g.autoSwitch) {
            // Pinned model first, but fall through to the next one when its free plan runs out.
            preferred = sel.removePrefix("PIN:")
            mode = ChatMode.AUTO
        } else if (lastModelKey != null && (adaptive || mode == ChatMode.AUTO) && cands.any { it.key == lastModelKey }) {
            preferred = lastModelKey
        }
        val visionPool = if (sel.startsWith("PIN:")) all.filter { it.key == sel.removePrefix("PIN:") }.ifEmpty { cands } else cands
        history = HistoryImages.prepare(
            history,
            HistoryImages.keep(needsVision, visionPool.map { it.model.vision }),
            app.getString(R.string.history_image_placeholder)
        ).toMutableList()
        val hint = if (adaptive || saver) kind.hint else ""
        val deep = if ((g.deepThink || (smart && adaptive && cls.difficulty == Difficulty.HARD)) && kind != TaskKind.QUICK) DEEP_THINK_PROMPT else ""
        val memory = if (memoryJob != null) {
            val m = memoryJob.await().filter { it.entry.answer.length <= 3000 }
            if (m.isEmpty()) "" else buildString {
                append("Past answers (use only if relevant):")
                m.forEach { append("\nQ: ").append(it.entry.question.take(200)).append("\nA: ").append(it.entry.answer.take(500)) }
            }
        } else ""
        val summaryText = if (summaryLine.isEmpty()) "" else ContextPlanner.asSystemLine(summaryLine)
        val pendingSearch = mandatorySearch
        if (pendingSearch != null) {
            val budgetLeft = (SEARCH_BUDGET_MS - (System.currentTimeMillis() - tSearch0)).coerceAtLeast(1L)
            val out = kotlinx.coroutines.withTimeoutOrNull(budgetLeft) { pendingSearch.await() }
            if (out == null) {
                if (lateSearch == null) lateSearch = pendingSearch else pendingSearch.cancel()
            } else {
                bundle = out.bundle
                if (bundle == null) {
                    searchNote = app.getString(
                        if (out.error != null) R.string.search_failed_note else R.string.search_none_note
                    )
                    if (out.error?.kind == com.freellm.chat.domain.ErrorKind.AUTH) {
                        notice.value = app.getString(R.string.search_key_rejected)
                    }
                    if (out.error?.kind == com.freellm.chat.domain.ErrorKind.POLICY) {
                        notice.value = app.getString(R.string.local_search_blocked)
                    }
                }
            }
        }
        val searchMs = System.currentTimeMillis() - tSearch0
        val styleNow = g.style
        bundle = bundle?.let { it.copy(items = it.items.take(AnswerPolicy.maxSources(styleNow))) }
        var toolSpecs: List<ToolSpec> = emptyList()
        val wsFiles = wsJob?.await() ?: false
        if (cont == null && !lastHasImages && g.toolsEnabled && !ToolPolicy.trivial(lastUser) && ToolPolicy.hasIntent(lastUser, wsFiles)) {
            val off = g.toolsOff
            val enabledTools = ToolCatalog.ids().filter { it !in off }.toSet()
            toolSpecs = ToolPolicy.select(
                lastUser, enabledTools, g.toolsMode,
                app.toolHost.hasRunner(), wsFiles
            )
        }
        val projectMode = ToolPolicy.projectMode(toolSpecs)
        val projectAsk = com.freellm.chat.domain.CodeFiles.wantsZip(lastUser) || com.freellm.chat.domain.CodeFiles.wantsProject(lastUser)
        val bigOutput = projectMode || projectAsk
        val sys = listOf(
            systemPrompt(cid, !projectMode && (kind == TaskKind.CODE || com.freellm.chat.domain.CodeFiles.wantsProject(lastUser)), g, guardLang ?: msgLang),
            if (followUp) CONTINUITY_PROMPT else "", hint, deep, memory, summaryText,
            AnswerPolicy.build(AnswerPolicy.spec(lastUser, styleNow, bundle)),
            if (toolSpecs.isNotEmpty()) ToolProtocol.system(toolSpecs, projectMode) else "",
            if (!projectMode && projectAsk) com.freellm.chat.domain.CodeFiles.ZIP_SYSTEM else "",
            if (projectMode || projectAsk) com.freellm.chat.domain.CodeFiles.stackRules(lastUser) else ""
        ).filter { it.isNotEmpty() }.joinToString("\n")
        val temp = if (g.answerTemperature) TemperaturePolicy.pick(kind, cls, bundle != null) else null
        val maxTok = when {
            bigOutput -> MaxTokensPlanner.project(cands.maxOfOrNull { it.model.contextWindow } ?: 0)
            cont != null -> null
            g.dynamicTokens -> MaxTokensPlanner.plan(kind, lastUser, g.style, hasAtt, bundle != null)
            saver && kind == TaskKind.QUICK -> QUICK_MAX_TOKENS
            else -> null
        }
        val wantCaps = if (smart && adaptive) cls.caps else emptyList()
        var msgs = listOf(ChatMsg("system", sys)) + history
        val maxWin = cands.maxOfOrNull { it.model.contextWindow } ?: 0
        if (maxWin > 0) msgs = RequestFitter.clampLast(msgs, maxWin - minOf(4096, maxWin / 4))
        val usedBundle = bundle?.fit((maxWin / 3).coerceIn(1200, 4500))
        if (usedBundle != null) {
            val li = msgs.indexOfLast { it.role == "user" }
            if (li >= 0) msgs = msgs.toMutableList().also { it[li] = it[li].copy(content = SourcesPrompt.inject(it[li].content, usedBundle)) }
        }
        val liveSources = usedBundle?.stored().orEmpty()
        var gotFirst = false
        var curAttempt: Target? = null
        var curAttemptNo = 0
        var attemptAt = 0L
        var attemptStatus: String? = null
        var progressNow: Float? = null
        var pushHook: (() -> Unit)? = null
        fun refreshAttempt() {
            val at = curAttempt
            if (at == null || gotFirst) {
                attemptStatus = null
                progressNow = null
                return
            }
            val waited = System.currentTimeMillis() - attemptAt
            if (needsVision) {
                val base = IMAGE_SEND_START + 0.04f * minOf(curAttemptNo - 1, 5)
                progressNow = minOf(IMAGE_WAIT_CAP, base + waited / IMAGE_WAIT_SPAN_MS * (IMAGE_WAIT_CAP - base))
            }
            attemptStatus = when {
                curAttemptNo > 1 -> app.getString(R.string.attempt_next, at.model.displayName, curAttemptNo)
                needsVision && waited < IMAGE_SEND_MS -> app.getString(R.string.image_send, at.model.displayName)
                needsVision -> app.getString(R.string.image_wait, at.model.displayName)
                else -> null
            }
        }
        val req = RouteRequest(
            messages = msgs,
            mode = mode,
            pinnedKey = if (mode == ChatMode.PINNED) sel.removePrefix("PIN:") else null,
            candidates = cands,
            preferredKey = preferred,
            maxTokens = maxTok,
            adaptive = adaptive,
            preferSpeed = adaptive && kind == TaskKind.QUICK,
            needsVision = needsVision,
            rating = { app.ratings.effective(it, wantCaps.contains("arabic")) },
            wantCaps = wantCaps,
            temperature = temp,
            proactive = g.budgetAlerts,
            estimatedTokens = RequestFitter.needed(msgs, maxTok),
            fast = adaptive && !needsVision && (kind == TaskKind.QUICK || cls.difficulty == Difficulty.EASY),
            lang = guardLang,
            firstMessage = cont == null && exclude == null && userTurns.size <= 1,
            onAttempt = { t, n, _ ->
                curAttempt = t
                curAttemptNo = n
                attemptAt = System.currentTimeMillis()
                refreshAttempt()
                pushHook?.invoke()
            }
        )
        val tPre = System.currentTimeMillis()
        var firstTokenAt = 0L
        var switches = 0

        val text = StringBuilder(prefix)
        val notes = mutableListOf<String>()
        searchNote?.let { notes.add(it) }
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
            if (saved >= 40) note += " \u00B7 " + app.getString(R.string.tokens_saved, saved)
            notes.add(note)
        }
        if (needsVision) notes.add(app.getString(R.string.vision_note))
        val hold = StringBuilder()
        var released = cont == null
        val tail = prefix.takeLast(TAIL_CHARS)
        var target: Target? = null
        var error: String? = null
        var pinnedFailure = false
        var noResume = false
        var completedOk = false
        var lastPush = 0L
        var toolStatus: String? = null
        val toolSteps = ArrayList<ToolStep>()

        fun push(force: Boolean = false) {
            val n = System.currentTimeMillis()
            if (force || n - lastPush >= 50L + minOf(150L, text.length / 40L)) {
                lastPush = n
                stream.value = StreamUi(text.toString(), notes.toList(), target, true, continuingId = cont?.id, sources = liveSources, status = toolStatus ?: attemptStatus, progress = progressNow)
            }
        }

        stream.value = StreamUi(text.toString(), active = true, continuingId = cont?.id, sources = liveSources, progress = if (needsVision) IMAGE_PREP_END else null)
        pushHook = { push(true) }
        val ticker = viewModelScope.launch {
            while (!gotFirst) {
                delay(TICK_MS)
                if (gotFirst) break
                refreshAttempt()
                push(true)
            }
        }
        val events = if (toolSpecs.isEmpty()) router.stream(req) else app.toolRunner.stream(
            req, ToolContext(cid, lastUser, projectMode = projectMode, projectFiles = { withContext(kotlinx.coroutines.Dispatchers.IO) { app.workspace.files(cid) } }),
            { n ->
                toolStatus = n?.let { app.getString(R.string.tool_running, it) }
                push(true)
            },
            { st ->
                toolSteps.add(st)
                notes.add("🔧 " + st.name + " \u00B7 " + st.summary.take(110))
                push(true)
            }
        )
        try {
            events.collect { ev ->
                when (ev) {
                    is RouteEvent.Started -> {
                        target = ev.target
                        gotFirst = true
                        attemptStatus = null
                        progressNow = null
                        push(true)
                    }
                    is RouteEvent.Delta -> {
                        gotFirst = true
                        attemptStatus = null
                        progressNow = null
                        if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                        if (released) {
                            text.append(ev.text)
                        } else {
                            hold.append(ev.text)
                            if (hold.length >= HOLD_CHARS) {
                                released = true
                                val rest = Overlap.strip(tail, hold.toString())
                                text.append(joinSep(prefix, rest)).append(rest)
                                hold.setLength(0)
                            }
                        }
                        push()
                    }
                    is RouteEvent.Switched -> {
                        switches++
                        target = ev.target
                        notes.add(app.getString(R.string.switched_to, ev.target.model.displayName))
                        notice.value = app.getString(R.string.switched_notice)
                        push(true)
                    }
                    is RouteEvent.NearLimit -> {
                        notes.add(app.getString(R.string.budget_near, ev.target.providerName, ev.percent))
                        push(true)
                    }
                    is RouteEvent.Restart -> {
                        text.setLength(0)
                        text.append(prefix)
                        push(true)
                    }
                    is RouteEvent.AuthFailed -> {
                        val name = all.firstOrNull { it.providerId == ev.providerId }?.providerName ?: ev.providerId
                        val keyCount = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.keysOf(ev.providerId.substringBefore('#')).size }
                        if (ev.code == 403) {
                            if (ev.repeated) notice.value = app.getString(R.string.provider_cooling_notice, name)
                        } else if (keyCount > 1) {
                            notice.value = app.getString(R.string.key_rejected_notice, name)
                        } else {
                            if ('#' !in ev.providerId) manager.markFailed(ev.providerId)
                            notice.value = app.getString(R.string.provider_disabled_notice, name)
                        }
                    }
                    is RouteEvent.Done -> {
                        target = ev.target
                        completedOk = true
                        push(true)
                    }
                    is RouteEvent.Failed -> {
                        error = app.failureText(ev)
                        noResume = ResumePolicy.skip(ev.kind, ev.reasons)
                        pinnedFailure = ev.pinned
                    }
                }
            }
        } finally {
            ticker.cancel()
            pushHook = null
            withContext(NonCancellable) {
                if (toolSteps.any { it.project && it.ok }) {
                    notes.add(app.getString(R.string.project_note, withContext(kotlinx.coroutines.Dispatchers.IO) { app.workspace.count(cid) }))
                }
                if (!released && hold.isNotEmpty()) {
                    val rest = Overlap.strip(tail, hold.toString())
                    text.append(joinSep(prefix, rest)).append(rest)
                }
                val finalText = text.toString()
                val t = target
                val cutNow = error != null && finalText.length > prefix.length
                val cutLabel = app.getString(R.string.reply_cut_off)
                var lateSources: List<StoredSource> = emptyList()
                val late = lateSearch
                if (late != null) {
                    val outcome = if (error == null && completedOk) kotlinx.coroutines.withTimeoutOrNull(4000) { late.await() } else null
                    if (outcome == null) late.cancel()
                    lateSources = outcome?.bundle?.let { b ->
                        b.copy(items = b.items.take(AnswerPolicy.maxSources(styleNow))).fit((maxWin / 3).coerceIn(1200, 4500)).stored()
                    }.orEmpty()
                }
                var savedId: Long? = null
                val alive = repo.conversation(cid) != null
                if (cont != null) {
                    if (alive && finalText.length > prefix.length && t != null) {
                        val changed = cont.modelKey != t.key
                        val allNotes = buildList {
                            com.freellm.chat.domain.CutMark.strip(cont.notes)?.let { add(it) }
                            addAll(notes)
                            if (changed) add(app.getString(R.string.continued_by, t.model.displayName))
                        }.let { com.freellm.chat.domain.CutMark.add(it, cutLabel, cutNow) }.joinToString("\n").ifEmpty { null }
                        repo.updateAssistant(
                            id = cont.id,
                            content = finalText,
                            modelName = if (changed) "${cont.modelName.orEmpty()} \u2192 ${t.model.displayName}" else cont.modelName,
                            providerName = t.providerName,
                            modelKey = t.key,
                            notes = allNotes
                        )
                    }
                } else if (alive && finalText.isNotBlank()) {
                    savedId = repo.addMessage(
                        MessageEntity(
                            conversationId = cid,
                            role = "assistant",
                            content = finalText,
                            modelName = t?.model?.displayName,
                            providerName = t?.providerName,
                            modelKey = t?.key,
                            notes = com.freellm.chat.domain.CutMark.add(notes.toList(), cutLabel, cutNow).joinToString("\n").ifEmpty { null },
                            createdAt = System.currentTimeMillis(),
                            sources = SourcesPrompt.encode(liveSources + lateSources)
                        )
                    )
                }
                if (error != null && finalText.isBlank() && cont == null && !hasAtt && !skipMemory) {
                    val fb = withContext(kotlinx.coroutines.Dispatchers.Default) { brain.best(lastUser, LOCAL_FALLBACK) }
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
                } else if (completedOk && error == null && finalText.isNotBlank() && t != null && !hasAtt &&
                    finalText.length > prefix.length && usedBundle == null && toolSteps.isEmpty() && g.brainLearn &&
                    BrainPolicy.canLearn(lastUser, finalText, stored.size, cls.needsFresh) &&
                    !com.freellm.chat.domain.LangGuard.isGreeting(lastUser) && !com.freellm.chat.domain.LangGuard.mismatch(msgLang, finalText)
                ) {
                    val learned = withContext(kotlinx.coroutines.Dispatchers.Default) { brain.learn(lastUser, finalText) }
                    if (learned != null) app.brainStore.save()
                }
                if (completedOk && cacheKey != null && usedBundle == null && toolSteps.isEmpty() && error == null && t != null && finalText.length > prefix.length &&
                    finalText.isNotBlank() && !OutputQuality.isPoor(finalText) &&
                    !com.freellm.chat.domain.LangGuard.isGreeting(lastUser) && !com.freellm.chat.domain.LangGuard.mismatch(msgLang, finalText)
                ) {
                    repo.cachePut(
                        AnswerCacheEntity(
                            cacheKey, LocalBrain.normalize(lastUser), finalText,
                            t.model.displayName, t.providerName, System.currentTimeMillis()
                        ),
                        cacheTtl
                    )
                }
                val sid = savedId
                if (sid != null && error == null && t != null && usedBundle != null &&
                    Verifier.shouldVerify(g.verifyAnswers, cls.needsFresh, cls.difficulty, true, finalText.isNotBlank())
                ) {
                    val srcBlock = usedBundle.block()
                    val answeredKey = t.key
                    val baseNotes = notes.joinToString("\n")
                    app.appScope.launch {
                        val v = Verifier.run(app.sideTasks, all, answeredKey, lastUser, srcBlock, finalText)
                        if (v != null && v.unsupported.isNotEmpty()) {
                            repo.setNotes(sid, listOf(Verifier.warning(v.unsupported), baseNotes).filter { it.isNotEmpty() }.joinToString("\n"))
                        }
                    }
                }
                if (cont == null || t != null) {
                    val end = System.currentTimeMillis()
                    app.timing.add(
                        com.freellm.chat.domain.TimingEntry(
                            end, tClass - t0, tPre - tClass - searchMs, searchMs,
                            if (firstTokenAt > 0) firstTokenAt - tPre else 0L, switches,
                            t?.model?.displayName.orEmpty(), end - t0, error == null && completedOk
                        )
                    )
                }
                if (summaryOn && error == null) maybeSummarize(cid)
                val err = error
                lastError = err
                lastNoResume = noResume
                lastPinned = pinnedFailure
                stream.value = if (err != null) StreamUi(error = err, pinnedFailure = pinnedFailure) else null
            }
        }
    }

    companion object {
        private const val TICK_MS = 500L
        private const val IMAGE_PREP_START = 0.05f
        private const val IMAGE_PREP_END = 0.30f
        private const val IMAGE_SEND_START = 0.32f
        private const val IMAGE_WAIT_CAP = 0.92f
        private const val IMAGE_WAIT_SPAN_MS = 45_000f
        private const val IMAGE_SEND_MS = 1_500L
        private const val SEARCH_BUDGET_MS = 7_000L
        private const val LATE_SEARCH_MS = 20_000L
        private const val HOLD_CHARS = 24
        private const val TAIL_CHARS = 120
        private const val MAX_PENDING = 6
        private const val SWEEP_MIN_AGE_MS = 600_000L
        private const val MAX_HISTORY_IMAGES = 4
        private const val MAX_RESUME = 2
        private const val MAX_REPAIR = 2
        private const val RESUME_DELAY_MS = 2_000L
        private const val SUMMARY_IDLE_MS = 6_000L
        private const val QUICK_MAX_TOKENS = 1800
        private const val FILE_BUDGET_LAST = 2200
        private const val FILE_BUDGET_OLD = 350
        private const val LOCAL_CONFIDENT = 0.93
        private const val LOCAL_FALLBACK = 0.40
        private const val FOLLOW_UP_CHARS = 80
        private const val CONTINUITY_PROMPT =
            "This is one continuous conversation. Earlier assistant replies may have been written by other models; treat them as your own. " +
                "Use the earlier messages to resolve short or vague follow-ups and pronouns, and continue naturally without restarting or introducing yourself."
        private const val DEEP_THINK_PROMPT =
            "Think it through, verify, then answer clearly."
        private const val SYSTEM_PROMPT =
            "Helpful assistant. Reply in the user's language."
        private const val CONTINUE_PROMPT =
            "Continue your previous reply exactly where it stopped. Do not repeat anything and do not add an introduction."
    }
}
