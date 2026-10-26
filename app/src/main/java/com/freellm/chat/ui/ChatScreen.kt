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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
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
import com.freellm.chat.domain.Target
import kotlinx.coroutines.launch
import java.util.Calendar

private sealed class Picker {
    data object Select : Picker()
    data object Continue : Picker()
    data object Retry : Picker()
    data class Regen(val m: MessageEntity) : Picker()
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
    val s = stream
    val generating = s?.active == true
    val pendingReq = s == null && messages.lastOrNull()?.role == "user"
    val voiceMissing = stringResource(R.string.voice_unavailable)

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
                    label = selectionLabel(selection, targets),
                    onMenu = { scope.launch { drawerState.open() } },
                    onModel = { picker = if (generating) Picker.Continue else Picker.Select },
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
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(visible, key = { it.id }) { m ->
                            MessageItem(
                                m = m,
                                canSwitch = lastIsAssistant && m.id == lastAssistantId && !generating,
                                onRegenerate = { picker = Picker.Regen(it) },
                                onSwitch = { picker = Picker.Continue },
                                onEdit = { editing = it },
                                onDelete = { vm.deleteMessage(it) }
                            )
                        }
                        if (s != null) {
                            item(key = "stream") {
                                StreamItem(
                                    s = s,
                                    onRetry = { vm.retryPending() },
                                    onPick = { picker = Picker.Retry },
                                    onTryOther = { vm.tryOther() },
                                    onDismiss = { vm.dismissError() }
                                )
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
                        vm.send(input)
                        input = ""
                    },
                    onStop = { vm.stop() },
                    onPlus = { picker = if (generating) Picker.Continue else Picker.Select },
                    onMic = {
                        try {
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

    val p = picker
    if (p != null) {
        ModelSheet(
            purpose = when (p) {
                is Picker.Select -> PickPurpose.SELECT
                is Picker.Continue -> PickPurpose.CONTINUE
                is Picker.Retry -> PickPurpose.RETRY
                is Picker.Regen -> PickPurpose.REGENERATE
            },
            selection = selection,
            targets = targets,
            health = { vm.health(it) },
            onPick = { code ->
                when (p) {
                    is Picker.Select -> vm.select(code)
                    is Picker.Continue -> vm.continueWith(code)
                    is Picker.Retry -> vm.retryPending(code)
                    is Picker.Regen -> vm.regenerate(p.m, code)
                }
                picker = null
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
private fun ChatTopBar(label: String, onMenu: () -> Unit, onModel: () -> Unit, onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) { MenuGlyph() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onModel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                .clickable(onClick = onNew),
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
        StarBurst(size = 56.dp)
        Spacer(Modifier.height(24.dp))
        Text(greeting, style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(cs.surfaceContainerHigh)
                .clickable(onClick = onProviders)
                .padding(horizontal = 22.dp, vertical = 14.dp),
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
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, prompt) ->
            Text(
                label,
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainerHigh)
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
    onPlus: () -> Unit,
    onMic: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            CircleButton(onClick = onPlus, background = cs.surfaceContainerHighest, size = 40.dp) {
                Icon(Icons.Filled.Add, stringResource(R.string.pick_title_select), Modifier.size(22.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    cursorBrush = SolidColor(cs.onSurface),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when {
                generating -> CircleButton(onClick = onStop, background = cs.primary) {
                    Icon(Icons.Filled.Stop, stringResource(R.string.stop), Modifier.size(20.dp), tint = cs.onPrimary)
                }
                input.isNotBlank() -> CircleButton(onClick = onSend, background = cs.primary) {
                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.send), Modifier.size(22.dp), tint = cs.onPrimary)
                }
                else -> CircleButton(onClick = onMic, background = cs.surfaceContainerHighest) {
                    Icon(Icons.Outlined.Mic, stringResource(R.string.voice_input), Modifier.size(22.dp))
                }
            }
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
                .padding(12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onNew)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.chats_label),
                Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
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
                        onDelete = { onDelete(c) },
                        onExport = { onExport(c) }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
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
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) cs.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            c.title,
            Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun NoteChip(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(14.dp), tint = cs.tertiary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun MessageItem(
    m: MessageEntity,
    canSwitch: Boolean,
    onRegenerate: (MessageEntity) -> Unit,
    onSwitch: () -> Unit,
    onEdit: (MessageEntity) -> Unit,
    onDelete: (MessageEntity) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
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
    Box(
        Modifier
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Text(
                            m.content,
                            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MarkdownText(m.content)
                m.notes?.split("\n")?.filter { it.isNotBlank() }?.forEach { NoteChip(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) { doCopy() }
                    ActionIcon(Icons.Outlined.Share, stringResource(R.string.share)) { doShare() }
                    ActionIcon(Icons.Outlined.Refresh, stringResource(R.string.regenerate_other)) { onRegenerate(m) }
                    if (canSwitch) {
                        ActionIcon(Icons.Outlined.SwapHoriz, stringResource(R.string.switch_continue)) { onSwitch() }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOfNotNull(m.modelName, m.providerName).joinToString(" · "),
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
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
            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = cs.error) }, onClick = {
                menu = false
                onDelete(m)
            })
        }
    }
}

@Composable
private fun PendingCard(onRetry: () -> Unit, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.pending_title), color = cs.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.text.isNotEmpty()) {
            MarkdownText(s.text)
        } else if (s.active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarBurst(size = 20.dp, spinning = true)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.typing), color = cs.onSurfaceVariant)
            }
        }
        if (s.active && s.text.isNotEmpty()) StarBurst(size = 18.dp, spinning = true)
        s.notes.forEach { NoteChip(it) }
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
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(err, color = cs.error)
                Text(stringResource(R.string.request_saved), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    OutlinedButton(onClick = onPick) { Text(stringResource(R.string.pick_model)) }
                }
                Row {
                    if (s.pinnedFailure) {
                        TextButton(onClick = onTryOther) { Text(stringResource(R.string.try_other_model)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}
