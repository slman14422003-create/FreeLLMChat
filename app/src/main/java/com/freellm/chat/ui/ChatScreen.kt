@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.freellm.chat.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.R
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.Target
import kotlinx.coroutines.launch

@Composable
private fun selectionLabel(sel: String, targets: List<Target>): String = when {
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
    var menu by remember { mutableStateOf(false) }
    var warnTarget by remember { mutableStateOf<Target?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var input by remember { mutableStateOf("") }
    val generating = stream?.active == true

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.notice.value = null
        }
    }
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }

    if (warnTarget != null) {
        AlertDialog(
            onDismissRequest = { warnTarget = null },
            confirmButton = { TextButton(onClick = { warnTarget = null }) { Text(stringResource(R.string.ok)) } },
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            vm.newChat()
                            scope.launch { drawerState.close() }
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_chat))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_chats)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { c ->
                            ConversationRow(
                                c = c,
                                selected = c.id == convId,
                                onOpen = {
                                    vm.open(c.id)
                                    scope.launch { drawerState.close() }
                                },
                                onRename = { renaming = c },
                                onDelete = { vm.delete(c.id) },
                                onExport = {
                                    vm.export(c.id) { text ->
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        ctx.startActivity(Intent.createChooser(send, null))
                                    }
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSettings()
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings), Modifier.weight(1f))
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, stringResource(R.string.menu))
                        }
                    },
                    title = {
                        Box {
                            TextButton(onClick = { menu = true }) {
                                Text(
                                    selectionLabel(selection, targets),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                val modes = listOf(
                                    "AUTO" to R.string.mode_auto,
                                    "FAST" to R.string.mode_fast,
                                    "SMART" to R.string.mode_smart,
                                    "CODE" to R.string.mode_code
                                )
                                modes.forEach { (k, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(label),
                                                color = if (selection == k) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            vm.select(k)
                                            menu = false
                                        }
                                    )
                                }
                                if (targets.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        stringResource(R.string.all_models),
                                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                targets.forEach { t ->
                                    val trailing: (@Composable () -> Unit)? = if (t.model.trainsOnData) {
                                        {
                                            IconButton(onClick = { warnTarget = t }) {
                                                Icon(
                                                    Icons.Filled.Warning,
                                                    stringResource(R.string.trains_title),
                                                    Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    } else null
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    t.model.displayName,
                                                    color = if (selection == "PIN:${t.key}") MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    t.providerName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = trailing,
                                        onClick = {
                                            vm.select("PIN:${t.key}")
                                            menu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) {
                            Icon(Icons.Filled.Add, stringResource(R.string.new_chat))
                        }
                    }
                )
            }
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
            ) {
                val listState = rememberLazyListState()
                val count = messages.size + (if (stream != null) 1 else 0)
                LaunchedEffect(count, stream?.text?.length) {
                    if (count > 0) listState.scrollToItem(count - 1, 100000)
                }
                Box(Modifier.weight(1f)) {
                    if (count == 0) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.how_can_i_help),
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (targets.isEmpty()) {
                                Spacer(Modifier.size(16.dp))
                                Text(
                                    stringResource(R.string.no_provider_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.size(12.dp))
                                Button(onClick = onProviders) { Text(stringResource(R.string.setup_provider)) }
                            }
                        }
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(messages, key = { it.id }) { m ->
                            MessageItem(m, onRegenerate = { vm.regenerate(it) }, onDelete = { vm.deleteMessage(it) })
                        }
                        val s = stream
                        if (s != null) {
                            item(key = "stream") {
                                StreamItem(s, onTryOther = { vm.tryOther() }, onDismiss = { vm.dismissError() })
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.type_message)) },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 6
                    )
                    Spacer(Modifier.width(8.dp))
                    if (generating) {
                        FilledIconButton(onClick = { vm.stop() }) {
                            Icon(Icons.Filled.Stop, stringResource(R.string.stop))
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                vm.send(input)
                                input = ""
                            },
                            enabled = input.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                        }
                    }
                }
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
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip12(selected)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            c.title,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, null) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                    open = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.export_chat)) }, onClick = {
                    open = false
                    onExport()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                    open = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun Modifier.clip12(selected: Boolean): Modifier =
    if (selected) this.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)) else this

@Composable
private fun MessageItem(
    m: MessageEntity,
    onRegenerate: (MessageEntity) -> Unit,
    onDelete: (MessageEntity) -> Unit
) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val isUser = m.role == "user"
    val copiedMsg = stringResource(R.string.copied)
    val doCopy = {
        clip.setText(AnnotatedString(m.content))
        Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show()
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
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Text(
                            m.content,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else {
            Column {
                MarkdownText(m.content)
                m.notes?.split("\n")?.filter { it.isNotBlank() }?.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = listOfNotNull(m.modelName, m.providerName).joinToString(" · ")
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { doCopy() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy), Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onRegenerate(m) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.regenerate_other), Modifier.size(16.dp))
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = {
                menu = false
                doCopy()
            })
            if (!isUser) {
                DropdownMenuItem(text = { Text(stringResource(R.string.regenerate_other)) }, onClick = {
                    menu = false
                    onRegenerate(m)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                menu = false
                onDelete(m)
            })
        }
    }
}

@Composable
private fun StreamItem(s: StreamUi, onTryOther: () -> Unit, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.text.isNotEmpty()) {
            MarkdownText(s.text)
        } else if (s.active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.typing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        s.notes.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val t = s.target
        if (t != null && s.active) {
            Text(
                "${t.model.displayName} · ${t.providerName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val err = s.error
        if (err != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(err, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.pinnedFailure) {
                            Button(onClick = onTryOther) { Text(stringResource(R.string.try_other_model)) }
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }
}
