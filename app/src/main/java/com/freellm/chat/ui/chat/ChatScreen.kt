@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.freellm.chat.ui

import android.content.Intent
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
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
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
import androidx.compose.material3.LinearProgressIndicator
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
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit, onProviders: () -> Unit, onLive: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streamState = vm.stream.collectAsStateWithLifecycle()
    val stream by remember { androidx.compose.runtime.derivedStateOf { streamState.value?.copy(text = "") } }
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
    val micDeniedMsg = stringResource(R.string.mic_denied)
    val micOpenSettings = stringResource(R.string.open_settings)
    val micFailedMsg = stringResource(R.string.mic_failed)
    val dictation = rememberDictation(
        getInput = { input },
        onText = { input = it },
        onDenied = {
            scope.launch {
                val r = snackbar.showSnackbar(micDeniedMsg, actionLabel = micOpenSettings)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        },
        onFailed = { scope.launch { snackbar.showSnackbar(micFailedMsg) } }
    )
    var instrFor by remember { mutableStateOf<ConversationEntity?>(null) }
    var templatesOpen by remember { mutableStateOf(false) }
    val localPrefs = remember { (ctx.applicationContext as com.freellm.chat.FreeLlmApp).local }
    val secretStore = remember { (ctx.applicationContext as com.freellm.chat.FreeLlmApp).secrets }
    var follow by remember { mutableStateOf(true) }
    val fetchAsk by vm.fetchAsk.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (input.isBlank()) input = secretStore.draft()
        localPrefs.clearDraft()
    }
    LaunchedEffect(input) {
        kotlinx.coroutines.delay(400)
        secretStore.saveDraft(input)
    }
    fetchAsk?.let { ask ->
        androidx.compose.material3.AlertDialog(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { vm.answerFetch(false) },
            title = { Text(stringResource(R.string.fetch_confirm_title)) },
            text = { Text(stringResource(R.string.fetch_confirm_body, ask.host)) },
            confirmButton = { androidx.compose.material3.Button(onClick = { vm.answerFetch(true) }) { Text(stringResource(R.string.fetch_allow)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.answerFetch(false) }) { Text(stringResource(R.string.fetch_deny)) } }
        )
    }
    val s = stream
    val generating = s?.active == true
    val pendingReq = s == null && messages.lastOrNull()?.role == "user"
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
    var cameraOn by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraOn = true else Toast.makeText(ctx, ctx.getString(R.string.camera_denied), Toast.LENGTH_LONG).show()
    }
    if (cameraOn) {
        CameraCapture(
            onDone = { uris ->
                cameraOn = false
                if (uris.isNotEmpty()) vm.attachImages(uris)
            },
            onClose = { cameraOn = false }
        )
    }
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
    var fileMsg by remember { mutableStateOf<MessageEntity?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val msg = fileMsg
        fileMsg = null
        if (uri != null && msg != null) vm.saveFile(uri, msg) {}
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val j = pdfJob
        if (uri != null && j != null) vm.savePdf(uri, j.text, j.images)
        pdfJob = null
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { privacy = false },
            confirmButton = { androidx.compose.material3.Button(onClick = { privacy = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.trains_title)) },
            text = { Text(stringResource(R.string.trains_body)) }
        )
    }

    val r = renaming
    if (r != null) {
        var title by remember(r.id) { mutableStateOf(r.title) }
        AlertDialog(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { renaming = null },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { instrFor = null },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { templatesOpen = false },
            confirmButton = { androidx.compose.material3.Button(onClick = { templatesOpen = false }) { Text(stringResource(R.string.ok)) } },
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { editing = null },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    vm.edit(ed, text)
                    editing = null
                }, enabled = text.isNotBlank()) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.edit)) },
            text = { OutlinedTextField(text, { text = it }, maxLines = 8, modifier = Modifier.fillMaxWidth()) }
        )
    }

    val side: @Composable () -> Unit = {
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
    val main: @Composable () -> Unit = {
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
                val nearBottom by remember {
                    androidx.compose.runtime.derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
                    }
                }
                LaunchedEffect(nearBottom, listState.isScrollInProgress) {
                    if (nearBottom) follow = true else if (listState.isScrollInProgress) follow = false
                }
                LaunchedEffect(count) {
                    androidx.compose.runtime.snapshotFlow { streamState.value?.text?.length }.collect {
                        if (count > 0 && follow) listState.scrollToItem(count - 1, 100000)
                    }
                }
                val seenIds = remember { HashSet<Long>() }
                val reduceMotion = LocalReduceMotion.current
                var readCount by remember { androidx.compose.runtime.mutableIntStateOf(count) }
                LaunchedEffect(nearBottom, count) {
                    if (nearBottom) readCount = count
                }
                val unread = (count - readCount).coerceAtLeast(0)
                val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
                val lastIsAssistant = messages.lastOrNull()?.role == "assistant"
                Box(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = count > 0 && !nearBottom,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .zIndex(1f),
                        enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None else scaleIn(tween(Motion.medium)) + fadeIn(tween(Motion.medium)),
                        exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None else scaleOut(tween(Motion.medium)) + fadeOut(tween(Motion.medium))
                    ) {
                        Box {
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = {
                                    follow = true
                                    scope.launch { listState.animateScrollToItem(count - 1) }
                                }
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.jump_to_bottom))
                            }
                            if (unread > 0) {
                                androidx.compose.material3.Badge(Modifier.align(Alignment.TopEnd)) { Text(unread.toString()) }
                            }
                        }
                    }
                    if (count == 0) {
                        EmptyState(
                            hasProviders = targets.isNotEmpty(),
                            onProviders = onProviders,
                            onSuggestion = { text ->
                                if (vm.send(text)) follow = true
                            }
                        )
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                    ) {
                        items(visible, key = { it.id }) { m ->
                            val playEnter = remember(m.id) { seenIds.add(m.id) && System.currentTimeMillis() - m.createdAt < 3000L }
                            MessageItem(
                                modifier = if (reduceMotion) Modifier else Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = tween(Motion.medium),
                                    fadeOutSpec = tween(Motion.medium)
                                ),
                                playEnter = playEnter,
                                m = m,
                                canSwitch = lastIsAssistant && m.id == lastAssistantId && !generating,
                                onRegenerate = { picker = Picker.Regen(it) },
                                onSwitch = { picker = Picker.Continue },
                                onCompare = { picker = Picker.Compare },
                                onEdit = { editing = it },
                                onDelete = { vm.deleteMessage(it) },
                                onPdf = {
                                    pdfJob = PdfJob(com.freellm.chat.domain.ThinkSplit.strip(it.content), emptyList())
                                    savePdf.launch("chat.pdf")
                                },
                                onCard = {
                                    cardMsg = it
                                    saveCard.launch("reply.png")
                                },
                                onZip = {
                                    zipMsg = it
                                    saveZip.launch((com.freellm.chat.domain.CodeFiles.zipCommand(it.content) ?: (com.freellm.chat.domain.CodeFiles.projectName(com.freellm.chat.domain.CodeFiles.extractFinal(it.content)) + ".zip")))
                                },
                                onFile = {
                                    fileMsg = it
                                    saveFile.launch(com.freellm.chat.domain.CodeFiles.extractFinal(it.content).firstOrNull()?.path?.substringAfterLast('/') ?: "file.txt")
                                },
                                onFix = { vm.repairProject() },
                                onSpeak = { speaker.toggle(com.freellm.chat.domain.CodeFiles.stripCommand(com.freellm.chat.domain.ThinkSplit.strip(it.content))) },
                                onRate = { msg, good -> vm.rate(msg, good) },
                                onContinue = { vm.continueWith(null) }
                            )
                        }
                        if (s != null) {
                            item(key = "stream") {
                                Box(if (reduceMotion) Modifier else Modifier.animateItem(fadeInSpec = null, placementSpec = tween(Motion.medium), fadeOutSpec = tween(Motion.medium))) {
                                StreamItem(
                                    s = streamState.value ?: s,
                                    onRetry = { vm.retryPending() },
                                    onPick = { picker = Picker.Retry },
                                    onTryOther = { vm.tryOther() },
                                    onDismiss = { vm.dismissError() },
                                    onNextModel = { vm.retryWithNextModel() }
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
                Composer(
                    input = input,
                    onInput = {
                        if (it.isNotEmpty()) vm.warm()
                        input = it
                    },
                    generating = generating,
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (vm.send(input)) {
                            follow = true
                            input = ""
                        }
                    },
                    onStop = { vm.stop() },
                    pending = pendingAtt,
                    importing = importingAtt,
                    onPickImage = { pickImages.launch("image/*") },
                    onCamera = {
                        val has = androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx, android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (has) cameraOn = true else askCamera.launch(android.Manifest.permission.CAMERA)
                    },
                    onPickFile = { pickFile.launch(arrayOf("*/*")) },
                    onPickPdfImages = { pickPdfImages.launch(arrayOf("application/pdf")) },
                    onRemove = { vm.removePending(it) },
                    onQuick = { prompt ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (vm.send(if (input.isBlank()) prompt else prompt + "\n" + input.trim())) {
                            follow = true
                            input = ""
                        }
                    },
                    onImagesToPdf = {
                        pdfJob = PdfJob(null, pendingAtt.filter { it.isImage })
                        savePdf.launch("images.pdf")
                    },
                    onPlus = { picker = if (generating) Picker.Continue else Picker.Model },
                    listening = dictation.listening(),
                    micLevel = dictation.level(),
                    micStatic = dictation.powerSave(),
                    onMic = { dictation.toggle() },
                    onMicCancel = { dictation.cancel() },
                    onLive = onLive
                )
            }
        }
    }
    if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            side()
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).fillMaxHeight().background(cs.background),
                contentAlignment = Alignment.TopCenter
            ) {
                androidx.compose.foundation.layout.Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) { main() }
            }
        }
    } else {
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = { side() }) { main() }
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
            quota = { vm.quota(it) },
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
