@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.freellm.chat.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.freellm.chat.data.Attachment
import com.freellm.chat.data.AttachmentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.R
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.SourcesPrompt
import com.freellm.chat.domain.StoredSource
import com.freellm.chat.domain.Target
import com.freellm.chat.domain.UrlPolicy
import kotlinx.coroutines.launch
import java.util.Calendar

private sealed class Picker {
    data object Mode : Picker()
    data object Model : Picker()
    data object Continue : Picker()
    data object Retry : Picker()
    data class Regen(val m: MessageEntity) : Picker()
    data object Compare : Picker()
}

@Composable
private fun selectionLabel(sel: String, targets: List<Target>): String = when {
    sel == "ADAPT" -> stringResource(R.string.mode_adaptive)
    sel == "FAST" -> stringResource(R.string.mode_fast)
    sel == "SMART" -> stringResource(R.string.mode_smart)
    sel == "CODE" -> stringResource(R.string.mode_code)
    sel.startsWith("PIN:") -> {
        val k = sel.removePrefix("PIN:")
        targets.firstOrNull { it.key == k }?.model?.displayName ?: k
    }
    else -> stringResource(R.string.mode_auto)
}

@Composable
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit, onProviders: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val stream by vm.stream.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val convId by vm.conversationId.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var privacy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var instrFor by remember { mutableStateOf<ConversationEntity?>(null) }
    var templatesOpen by remember { mutableStateOf(false) }
    val localPrefs = remember { (ctx.applicationContext as com.freellm.chat.FreeLlmApp).local }
    LaunchedEffect(Unit) { if (input.isBlank()) input = localPrefs.draft() }
    LaunchedEffect(input) {
        kotlinx.coroutines.delay(400)
        localPrefs.saveDraft(input)
    }
    val s = stream
    val generating = s?.active == true
    val pendingReq = s == null && messages.lastOrNull()?.role == "user"
    val voiceMissing = stringResource(R.string.voice_unavailable)
    val pendingAtt by vm.pending.collectAsStateWithLifecycle()
    val importingAtt by vm.importing.collectAsStateWithLifecycle()
    var pdfJob by remember { mutableStateOf<PdfJob?>(null) }
    val speaker = remember { Speaker(ctx) }
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.attachImages(uris)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachFile(uri)
    }
    val pickPdfImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachPdfAsImages(uri)
    }
    var cardMsg by remember { mutableStateOf<MessageEntity?>(null) }
    val darkCard = androidx.compose.foundation.isSystemInDarkTheme()
    val saveCard = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val msg = cardMsg
        cardMsg = null
        if (uri != null && msg != null) {
            val rtl = androidx.compose.ui.text.intl.Locale.current.language == "ar"
            vm.saveCard(uri, msg, rtl, darkCard) { ok ->
                if (ok) {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.share_card_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
    var dictOn by remember { mutableStateOf(false) }
    val appCtx = ctx.applicationContext as com.freellm.chat.FreeLlmApp
    val dictSetting by appCtx.settings.voiceDictation.collectAsStateWithLifecycle(initialValue = true)
    val sttSetting by appCtx.settings.voiceStt.collectAsStateWithLifecycle(initialValue = "auto")
    val askMicDict = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictOn = true else Toast.makeText(ctx, ctx.getString(R.string.voice_denied), Toast.LENGTH_LONG).show()
    }
    if (dictOn) {
        WhisperDictation(
            onText = { t -> input = if (input.isBlank()) t else input.trimEnd() + " " + t },
            onClose = { dictOn = false }
        )
    }
    var voiceOn by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceOn = true else Toast.makeText(ctx, ctx.getString(R.string.voice_denied), Toast.LENGTH_LONG).show()
    }
    if (voiceOn) VoiceChat(vm, speaker) { voiceOn = false; speaker.stop() }
    var zipMsg by remember { mutableStateOf<MessageEntity?>(null) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val msg = zipMsg
        zipMsg = null
        if (uri != null && msg != null) {
            vm.saveZip(uri, msg) { ok ->
                if (ok) {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.zip_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val j = pdfJob
        if (uri != null && j != null) vm.savePdf(uri, j.text, j.images)
        pdfJob = null
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) {
            input = if (input.isBlank()) said else input.trimEnd() + " " + said
        }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.notice.value = null
        }
    }
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }

    if (privacy) {
        AlertDialog(
            onDismissRequest = { privacy = false },
            confirmButton = { TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.trains_title)) },
            text = { Text(stringResource(R.string.trains_body)) }
        )
    }

    val r = renaming
    if (r != null) {
        var title by remember(r.id) { mutableStateOf(r.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(r.id, title)
                    renaming = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) }
        )
    }

    val ins = instrFor
    if (ins != null) {
        var text by remember(ins.id) { mutableStateOf(ins.instructions.orEmpty()) }
        AlertDialog(
            onDismissRequest = { instrFor = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.setConversationInstructions(ins.id, text)
                    instrFor = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { instrFor = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.convo_instructions)) },
            text = {
                OutlinedTextField(
                    text, { text = it }, maxLines = 8, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.convo_instructions_hint)) }
                )
            }
        )
    }

    if (templatesOpen) {
        var list by remember { mutableStateOf(localPrefs.templates()) }
        var draftTpl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { templatesOpen = false },
            confirmButton = { TextButton(onClick = { templatesOpen = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.templates)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t,
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        input = if (input.isBlank()) t else input.trimEnd() + "\n" + t
                                        templatesOpen = false
                                    }
                                    .padding(vertical = Spacing.md),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                list = list.filterIndexed { j, _ -> j != i }
                                localPrefs.saveTemplates(list)
                            }) { Text(stringResource(R.string.delete), color = cs.error) }
                        }
                    }
                    OutlinedTextField(
                        draftTpl, { draftTpl = it }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.template_hint)) }
                    )
                    TextButton(
                        onClick = {
                            list = list + draftTpl.trim()
                            localPrefs.saveTemplates(list)
                            draftTpl = ""
                        },
                        enabled = draftTpl.isNotBlank()
                    ) { Text(stringResource(R.string.add_template)) }
                }
            }
        )
    }

    val ed = editing
    if (ed != null) {
        var text by remember(ed.id) { mutableStateOf(ed.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit(ed, text)
                    editing = null
                }, enabled = text.isNotBlank()) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.edit)) },
            text = { OutlinedTextField(text, { text = it }, maxLines = 8, modifier = Modifier.fillMaxWidth()) }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                query = query,
                onQuery = { vm.query.value = it },
                conversations = conversations,
                selectedId = convId,
                onNew = {
                    vm.newChat()
                    scope.launch { drawerState.close() }
                },
                onOpen = {
                    vm.open(it)
                    scope.launch { drawerState.close() }
                },
                onRename = { renaming = it },
                onStar = { vm.toggleStar(it) },
                onInstructions = { instrFor = it },
                onTemplates = { templatesOpen = true },
                onDelete = { vm.delete(it.id) },
                onExport = { c ->
                    vm.export(c.id) { text ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        ctx.startActivity(Intent.createChooser(send, null))
                    }
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onSettings()
                }
            )
        }
    ) {
        Scaffold(
            containerColor = cs.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                ChatTopBar(
                    active = generating,
                    label = selectionLabel(selection, targets),
                    onMenu = { scope.launch { drawerState.open() } },
                    onModel = { picker = Picker.Mode },
                    onNew = { vm.newChat() }
                )
            }
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
            ) {
                val listState = rememberLazyListState()
                val visible = messages.filter { m -> !(s != null && s.active && s.continuingId == m.id) }
                val count = visible.size + (if (s != null) 1 else 0) + (if (pendingReq) 1 else 0)
                LaunchedEffect(count, s?.text?.length) {
                    if (count > 0) listState.scrollToItem(count - 1, 100000)
                }
                val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
                val lastIsAssistant = messages.lastOrNull()?.role == "assistant"
                Box(Modifier.weight(1f)) {
                    if (count == 0) {
                        EmptyState(hasProviders = targets.isNotEmpty(), onProviders = onProviders)
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                    ) {
                        items(visible, key = { it.id }) { m ->
                            MessageItem(
                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                m = m,
                                canSwitch = lastIsAssistant && m.id == lastAssistantId && !generating,
                                onRegenerate = { picker = Picker.Regen(it) },
                                onSwitch = { picker = Picker.Continue },
                                onCompare = { picker = Picker.Compare },
                                onEdit = { editing = it },
                                onDelete = { vm.deleteMessage(it) },
                                onPdf = {
                                    pdfJob = PdfJob(it.content, emptyList())
                                    savePdf.launch("chat.pdf")
                                },
                                onCard = {
                                    cardMsg = it
                                    saveCard.launch("reply.png")
                                },
                                onZip = {
                                    zipMsg = it
                                    saveZip.launch(com.freellm.chat.domain.CodeFiles.projectName(com.freellm.chat.domain.CodeFiles.extract(it.content)) + ".zip")
                                },
                                onSpeak = { speaker.toggle(it.content) },
                                onRate = { msg, good -> vm.rate(msg, good) }
                            )
                        }
                        if (s != null) {
                            item(key = "stream") {
                                Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                                StreamItem(
                                    s = s,
                                    onRetry = { vm.retryPending() },
                                    onPick = { picker = Picker.Retry },
                                    onTryOther = { vm.tryOther() },
                                    onDismiss = { vm.dismissError() }
                                )
                                }
                            }
                        }
                        if (pendingReq) {
                            item(key = "pending") {
                                PendingCard(
                                    onRetry = { vm.retryPending() },
                                    onPick = { picker = Picker.Retry }
                                )
                            }
                        }
                    }
                }
                if (count == 0) {
                    SuggestionRow(onPick = { input = it })
                }
                Composer(
                    input = input,
                    onInput = { input = it },
                    generating = generating,
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.send(input)
                        input = ""
                    },
                    onStop = { vm.stop() },
                    pending = pendingAtt,
                    importing = importingAtt,
                    onPickImage = { pickImages.launch("image/*") },
                    onPickFile = { pickFile.launch(arrayOf("*/*")) },
                    onPickPdfImages = { pickPdfImages.launch(arrayOf("application/pdf")) },
                    onRemove = { vm.removePending(it) },
                    onQuick = { prompt ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.send(if (input.isBlank()) prompt else prompt + "\n" + input.trim())
                        input = ""
                    },
                    onImagesToPdf = {
                        pdfJob = PdfJob(null, pendingAtt.filter { it.isImage })
                        savePdf.launch("images.pdf")
                    },
                    onPlus = { picker = if (generating) Picker.Continue else Picker.Model },
                    onVoiceChat = {
                        val has = androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (has) voiceOn = true else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onMic = {
                        val whisper = dictSetting && sttSetting != "device" && appCtx.groqVoice.hasKey()
                        if (whisper) {
                            val has = androidx.core.content.ContextCompat.checkSelfPermission(
                                ctx, android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (has) dictOn = true else askMicDict.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else try {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, voiceMissing, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    val cmp by vm.compareUi.collectAsStateWithLifecycle()
    val mergeOn by (ctx.applicationContext as com.freellm.chat.FreeLlmApp).settings.compareMerge
        .collectAsStateWithLifecycle(initialValue = true)
    val cu = cmp
    if (cu != null) {
        CompareScreen(
            ui = cu,
            mergeEnabled = mergeOn,
            onChoose = { vm.choose(it) },
            onMerge = { vm.merge() },
            onChooseMerged = { vm.chooseMerged() },
            onClose = { vm.dismissCompare() }
        )
    }

    val p = picker
    if (p != null) {
        ModelSheet(
            purpose = when (p) {
                is Picker.Mode -> PickPurpose.MODE
                is Picker.Model -> PickPurpose.MODEL
                is Picker.Continue -> PickPurpose.CONTINUE
                is Picker.Retry -> PickPurpose.RETRY
                is Picker.Regen -> PickPurpose.REGENERATE
                is Picker.Compare -> PickPurpose.COMPARE
            },
            selection = selection,
            targets = targets,
            health = { vm.health(it) },
            latency = { vm.latency(it) },
            onPick = { code ->
                when (p) {
                    is Picker.Mode, is Picker.Model -> vm.select(code)
                    is Picker.Continue -> vm.continueWith(code)
                    is Picker.Retry -> vm.retryPending(code)
                    is Picker.Regen -> vm.regenerate(p.m, code)
                    is Picker.Compare -> Unit
                }
                picker = null
            },
            onCompare = { a, b ->
                picker = null
                vm.compare(a, b)
            },
            onPrivacy = { privacy = true },
            onProviders = {
                picker = null
                onProviders()
            },
            onDismiss = { picker = null }
        )
    }
}

@Composable
private fun ChatTopBar(active: Boolean, label: String, onMenu: () -> Unit, onModel: () -> Unit, onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .bounceClick(onMenu),
            contentAlignment = Alignment.Center
        ) { MenuGlyph() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Corners.lg))
                    .clickable(onClick = onModel)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveOrb(active)
                Spacer(Modifier.width(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .bounceClick(onNew),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Edit, stringResource(R.string.new_chat), Modifier.size(22.dp)) }
    }
}

@Composable
private fun EmptyState(hasProviders: Boolean, onProviders: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChatHeroArt(dim = 56.dp, modifier = Modifier.appear(0))
        Spacer(Modifier.height(8.dp))
        Text(greeting, style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(Corners.xl))
                .background(cs.surfaceContainerHigh)
                .clickable(onClick = onProviders)
                .padding(horizontal = 22.dp, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.plan_free), color = cs.onSurfaceVariant, fontSize = 16.sp)
            Text("  ·  ", color = cs.onSurfaceVariant, fontSize = 16.sp)
            Text(
                stringResource(R.string.add_provider),
                color = cs.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!hasProviders) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_provider_hint),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SuggestionRow(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val items = listOf(
        stringResource(R.string.sug_explain) to stringResource(R.string.sug_explain_p),
        stringResource(R.string.sug_code) to stringResource(R.string.sug_code_p),
        stringResource(R.string.sug_summarize) to stringResource(R.string.sug_summarize_p),
        stringResource(R.string.sug_translate) to stringResource(R.string.sug_translate_p)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items.forEachIndexed { idx, (label, prompt) ->
            Text(
                label,
                Modifier
                    .appear(idx)
                    .clip(RoundedCornerShape(Corners.lg))
                    .background(cs.surfaceContainerHigh)
                    .bounceClick { onPick(prompt) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                fontSize = 14.sp,
                color = cs.onSurface
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    pending: List<Attachment>,
    importing: Boolean,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickPdfImages: () -> Unit,
    onRemove: (Attachment) -> Unit,
    onQuick: (String) -> Unit,
    onImagesToPdf: () -> Unit,
    onPlus: () -> Unit,
    onVoiceChat: () -> Unit = {},
    onMic: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var attachMenu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val glow = rememberInfiniteTransition(label = "glow")
    val pulse = glow.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )
    val focusAmt = animateFloatAsState(if (focused) 1f else 0f, tween(Motion.Normal), label = "focusAmt")
    val accent = cs.primary
    Surface(
        shape = RoundedCornerShape(Corners.xl),
        color = cs.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .drawWithContent {
                drawContent()
                val a = if (generating) pulse.value else focusAmt.value * 0.85f
                if (a > 0.01f) {
                    drawRoundRect(
                        color = accent.copy(alpha = a),
                        cornerRadius = CornerRadius(30.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
    ) {
        Column {
        if (pending.isNotEmpty() || importing) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pending.forEach { a -> PendingChip(a, onRemove = { onRemove(a) }) }
                if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            val qTranslate = stringResource(R.string.qa_translate_prompt)
            val qSummary = stringResource(R.string.qa_summarize_prompt)
            val qExtract = stringResource(R.string.qa_extract_prompt)
            val qExplain = stringResource(R.string.qa_explain_prompt)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                QuickChip(stringResource(R.string.qa_translate)) { onQuick(qTranslate) }
                QuickChip(stringResource(R.string.qa_summarize)) { onQuick(qSummary) }
                QuickChip(stringResource(R.string.qa_extract)) { onQuick(qExtract) }
                QuickChip(stringResource(R.string.qa_explain)) { onQuick(qExplain) }
                if (pending.any { it.isImage }) QuickChip(stringResource(R.string.qa_images_pdf)) { onImagesToPdf() }
            }
        }
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.Bottom) {
            CircleButton(onClick = onPlus, background = cs.surfaceContainerHighest, size = 40.dp) {
                Icon(Icons.Filled.Add, stringResource(R.string.pick_title_select), Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box {
                CircleButton(onClick = { attachMenu = true }, background = cs.surfaceContainerHighest, size = 40.dp) {
                    Icon(Icons.Outlined.AttachFile, stringResource(R.string.attach), Modifier.size(22.dp))
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.attach_photo)) }, onClick = {
                        attachMenu = false
                        onPickImage()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.attach_file)) }, onClick = {
                        attachMenu = false
                        onPickFile()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.attach_pdf_images)) }, onClick = {
                        attachMenu = false
                        onPickPdfImages()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.voice_chat)) }, onClick = {
                        attachMenu = false
                        onVoiceChat()
                    })
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(stringResource(R.string.type_message), color = cs.onSurfaceVariant, fontSize = 16.sp)
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    textStyle = TextStyle(
                        color = cs.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            val trailing = when {
                generating -> 0
                input.isNotBlank() || pending.isNotEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = trailing,
                transitionSpec = {
                    (scaleIn(tween(Motion.Normal, easing = FastOutSlowInEasing), initialScale = 0.4f) + fadeIn(tween(Motion.Fast))) togetherWith
                        (scaleOut(tween(Motion.Fast), targetScale = 0.4f) + fadeOut(tween(Motion.Fast)))
                },
                label = "trailing"
            ) { st ->
                when (st) {
                    0 -> CircleButton(onClick = onStop, background = cs.primary) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.stop), Modifier.size(20.dp), tint = cs.onPrimary)
                    }
                    1 -> CircleButton(onClick = onSend, background = cs.primary) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.send), Modifier.size(22.dp), tint = cs.onPrimary)
                    }
                    else -> CircleButton(onClick = onMic, background = cs.surfaceContainerHighest) {
                        Icon(Icons.Outlined.Mic, stringResource(R.string.voice_input), Modifier.size(22.dp))
                    }
                }
            }
        }
        }
    }
}

private class PdfJob(val text: String?, val images: List<Attachment>)

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Corners.md),
        color = cs.surfaceContainerHighest
    ) {
        Text(label, Modifier.padding(horizontal = Spacing.md, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AttachThumb(a: Attachment, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    if (a.isImage) {
        val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.path) {
            value = withContext(Dispatchers.IO) {
                (ctx.applicationContext as com.freellm.chat.FreeLlmApp).attachments.thumb(a, 200)?.asImageBitmap()
            }
        }
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(Corners.sm))
                .background(cs.surfaceContainerHighest)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        ) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    } else {
        Row(
            Modifier
                .height(size)
                .widthIn(max = 180.dp)
                .clip(RoundedCornerShape(Corners.sm))
                .background(cs.surfaceContainerHighest)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Description, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PendingChip(a: Attachment, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box {
        AttachThumb(a, 56.dp)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(RoundedCornerShape(Corners.sm))
                .background(cs.inverseSurface)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(14.dp), tint = cs.inverseOnSurface)
        }
    }
}

@Composable
private fun ChatDrawer(
    query: String,
    onQuery: (String) -> Unit,
    conversations: List<ConversationEntity>,
    selectedId: Long?,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onStar: (ConversationEntity) -> Unit,
    onInstructions: (ConversationEntity) -> Unit,
    onTemplates: () -> Unit,
    onDelete: (ConversationEntity) -> Unit,
    onExport: (ConversationEntity) -> Unit,
    onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalDrawerSheet(drawerContainerColor = cs.surfaceContainerLow, modifier = Modifier.width(320.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Spacing.md)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.card))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_chats), color = cs.onSurfaceVariant, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(cs.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .clip(RoundedCornerShape(Corners.md))
                    .clickable(onClick = onNew)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.md))
                    .clickable(onClick = onTemplates)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.templates), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.chats_label),
                Modifier.padding(start = Spacing.md, top = Spacing.lg, bottom = Spacing.xs),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { c ->
                    ConversationRow(
                        c = c,
                        selected = c.id == selectedId,
                        onOpen = { onOpen(c.id) },
                        onRename = { onRename(c) },
                        onStar = { onStar(c) },
                        onInstructions = { onInstructions(c) },
                        onDelete = { onDelete(c) },
                        onExport = { onExport(c) }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.md))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.tertiary),
                    contentAlignment = Alignment.Center
                ) { Text("F", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.plan_free),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings), Modifier.size(22.dp), tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConversationRow(
    c: ConversationEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onStar: () -> Unit,
    onInstructions: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.md))
            .background(if (selected) cs.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (c.starred) {
            Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = cs.tertiary)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            c.title,
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.md),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { open = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(if (c.starred) R.string.unpin_chat else R.string.pin_chat)) }, onClick = {
                    open = false
                    onStar()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.convo_instructions)) }, onClick = {
                    open = false
                    onInstructions()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                    open = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.export_chat)) }, onClick = {
                    open = false
                    onExport()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = cs.error) }, onClick = {
                    open = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .bounceClick(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ToolTrace(lines: List<String>, expandedByDefault: Boolean = false) {
    var open by remember { mutableStateOf(expandedByDefault) }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(Corners.md))
            .background(cs.surfaceContainerHigh)
            .clickable { open = !open }
            .padding(horizontal = Spacing.md, vertical = 6.dp)
    ) {
        Text(
            stringResource(R.string.tools_used, lines.size),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant
        )
        if (open) {
            lines.forEach { l ->
                Text(
                    l.removePrefix("🔧").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NoteChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MessageItem(
    modifier: Modifier = Modifier,
    m: MessageEntity,
    canSwitch: Boolean,
    onRegenerate: (MessageEntity) -> Unit,
    onSwitch: () -> Unit,
    onCompare: () -> Unit = {},
    onEdit: (MessageEntity) -> Unit,
    onDelete: (MessageEntity) -> Unit,
    onPdf: (MessageEntity) -> Unit,
    onCard: (MessageEntity) -> Unit,
    onZip: (MessageEntity) -> Unit = {},
    onSpeak: (MessageEntity) -> Unit,
    onRate: (MessageEntity, Boolean) -> Unit = { _, _ -> }
) {
    val cs = MaterialTheme.colorScheme
    var rated by remember(m.id) { mutableStateOf<Boolean?>(null) }
    val hasZip = remember(m.content, m.notes) {
        m.role != "user" && (m.notes?.contains("📁") == true || com.freellm.chat.domain.CodeFiles.extract(m.content).isNotEmpty())
    }
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val isLocal = m.modelName == stringResource(R.string.local_brain_name)
    val isUser = m.role == "user"
    val copiedMsg = stringResource(R.string.copied)
    val doCopy = {
        clip.setText(AnnotatedString(m.content))
        Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show()
    }
    val doShare = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, m.content)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }
    val fresh = remember { m.role == "user" && System.currentTimeMillis() - m.createdAt < 2000 }
    Box(
        modifier
            .popIn(fresh)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { menu = true },
                onClick = {}
            )
    ) {
        if (isUser) {
            val dir = detectDirection(m.content) ?: LocalLayoutDirection.current
            val atts = remember(m.attachments) { AttachmentCodec.decode(m.attachments) }
            var preview by remember { mutableStateOf<Attachment?>(null) }
            preview?.let { ImagePreview(it) { preview = null } }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
              if (atts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    atts.forEach { att -> AttachThumb(att, 96.dp, if (att.isImage) ({ preview = att }) else null) }
                }
              }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(
                        shape = RoundedCornerShape(Corners.card),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Text(
                            m.content,
                            Modifier.padding(horizontal = Spacing.lg, vertical = 11.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MarkdownText(m.content)
                if (!m.modelName.isNullOrBlank()) ModelBadge(m.modelName)
                val srcs = remember(m.sources) { SourcesPrompt.decode(m.sources) }
                if (srcs.isNotEmpty()) SourcesRow(srcs)
                val warn = m.notes?.lines()?.firstOrNull { it.startsWith("⚠") }
                if (warn != null) {
                    Text(
                        warn,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isLocal) {
                    m.notes?.split("\n")?.filter { it.isNotBlank() && !it.startsWith("⚠") && !it.startsWith("🔧") }?.take(1)?.forEach { NoteChip(it) }
                }
                val toolLines = m.notes?.lines()?.filter { it.startsWith("🔧") }.orEmpty()
                if (toolLines.isNotEmpty()) ToolTrace(toolLines)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) { doCopy() }
                    ActionIcon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.read_aloud)) { onSpeak(m) }
                    ActionIcon(Icons.Outlined.Refresh, stringResource(R.string.regenerate_other)) { onRegenerate(m) }
                    if (canSwitch) ActionIcon(Icons.Outlined.Compare, stringResource(R.string.compare_action)) { onCompare() }
                    Box {
                        ActionIcon(Icons.Filled.MoreVert, stringResource(R.string.more)) { more = true }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rate_good)) },
                                leadingIcon = { Icon(if (rated == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null) },
                                onClick = {
                                    more = false
                                    rated = true
                                    onRate(m, true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rate_bad)) },
                                leadingIcon = { Icon(Icons.Outlined.ThumbDown, null) },
                                onClick = {
                                    more = false
                                    rated = false
                                    onRate(m, false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    more = false
                                    doShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_pdf)) },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                onClick = {
                                    more = false
                                    onPdf(m)
                                }
                            )
                            if (hasZip) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.zip_save)) },
                                    leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                    onClick = {
                                        more = false
                                        onZip(m)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_card)) },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                onClick = {
                                    more = false
                                    onCard(m)
                                }
                            )
                            if (canSwitch) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.switch_continue)) },
                                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                    onClick = {
                                        more = false
                                        onSwitch()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = {
                menu = false
                doCopy()
            })
            if (isUser) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                    menu = false
                    onEdit(m)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.google_ai_mode)) }, onClick = {
                    menu = false
                    val q = java.net.URLEncoder.encode(m.content, "UTF-8")
                    val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?udm=50&q=" + q))
                    runCatching { ctx.startActivity(i) }
                })
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.regenerate_other)) }, onClick = {
                    menu = false
                    onRegenerate(m)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.save_pdf)) }, onClick = {
                menu = false
                onPdf(m)
            })
            if (hasZip) {
                DropdownMenuItem(text = { Text(stringResource(R.string.zip_save)) }, onClick = {
                    menu = false
                    onZip(m)
                })
            }
            if (!isUser) {
                DropdownMenuItem(text = { Text(stringResource(R.string.share_card)) }, onClick = {
                    menu = false
                    onCard(m)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = cs.error) }, onClick = {
                menu = false
                onDelete(m)
            })
        }
    }
}

@Composable
private fun ModelBadge(name: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        name,
        Modifier
            .clip(RoundedCornerShape(Corners.chip))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        style = MaterialTheme.typography.labelSmall,
        color = cs.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SourcesRow(sources: List<StoredSource>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Language, null, Modifier.size(14.dp), tint = cs.tertiary)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.search_badge), style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            sources.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(Corners.md))
                        .background(cs.surfaceContainerHigh)
                        .clickable { runCatching { uri.openUri(s.url) } }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("[" + (i + 1) + "]", style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        UrlPolicy.host(s.url).ifEmpty { s.title },
                        Modifier.widthIn(max = 160.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingCard(onRetry: () -> Unit, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.card))
            .background(cs.surfaceContainerHigh)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(stringResource(R.string.pending_title), color = cs.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onPick) { Text(stringResource(R.string.pick_model)) }
        }
    }
}

@Composable
private fun StreamItem(
    s: StreamUi,
    onRetry: () -> Unit,
    onPick: () -> Unit,
    onTryOther: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (s.text.isNotEmpty()) {
            MarkdownText(s.text)
        } else if (s.active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarBurst(size = 20.dp, spinning = true)
                Spacer(Modifier.width(10.dp))
                Text(s.status ?: stringResource(R.string.typing), color = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                TypingDots()
            }
        }
        if (s.sources.isNotEmpty()) SourcesRow(s.sources)
        if (s.active && s.text.isNotEmpty()) StarBurst(size = 18.dp, spinning = true)
        if (s.active && s.text.isNotEmpty() && s.status != null) NoteChip(s.status)
        s.notes.filter { !it.startsWith("🔧") }.take(1).forEach { NoteChip(it) }
        val liveTools = s.notes.filter { it.startsWith("🔧") }
        if (liveTools.isNotEmpty()) ToolTrace(liveTools, expandedByDefault = s.active)
        val t = s.target
        if (t != null && s.active) {
            Text(
                "${t.model.displayName} · ${t.providerName}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
        val err = s.error
        if (err != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.card))
                    .background(cs.surfaceContainerHigh)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(err, color = cs.error)
                Text(stringResource(R.string.request_saved), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    OutlinedButton(onClick = onPick) { Text(stringResource(R.string.pick_model)) }
                }
                Row {
                    TextButton(onClick = onTryOther) { Text(stringResource(R.string.try_other_model)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}
