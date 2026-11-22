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

@Composable
internal fun ChatDrawer(
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
                AppMark(40.dp)
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
internal fun ConversationRow(
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
