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
internal fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .bounceClick(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun ToolTrace(lines: List<String>, expandedByDefault: Boolean = false) {
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
internal fun NoteChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun MessageItem(
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
    onFile: (MessageEntity) -> Unit = {},
    onFix: () -> Unit = {},
    onSpeak: (MessageEntity) -> Unit,
    onRate: (MessageEntity, Boolean) -> Unit = { _, _ -> },
    onContinue: () -> Unit = {},
    playEnter: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var rated by remember(m.id) { mutableStateOf<Boolean?>(null) }
    val hasZip = remember(m.content, m.notes) {
        m.role != "user" && (m.notes?.contains("📁") == true || com.freellm.chat.domain.CodeFiles.extract(m.content).isNotEmpty())
    }
    val parts = remember(m.content) {
        if (m.role == "user") com.freellm.chat.domain.ThinkParts("", false, m.content) else com.freellm.chat.domain.ThinkSplit.split(m.content)
    }
    val clean = remember(parts) { if (m.role == "user") m.content else com.freellm.chat.domain.CodeFiles.stripCommand(parts.answer) }
    val zipName = remember(m.content) { if (m.role != "user") com.freellm.chat.domain.CodeFiles.zipCommand(m.content) else null }
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val isLocal = m.modelName == stringResource(R.string.local_brain_name)
    val isUser = m.role == "user"
    val copiedMsg = stringResource(R.string.copied)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val doCopy = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        clip.setText(AnnotatedString(clean))
        Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show()
    }
    val doShare = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, clean)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }
    Box(
        modifier
            .messageEnter(playEnter, isUser)
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
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .background(AccentGradient, RoundedCornerShape(Corners.card))
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
                if (parts.thoughts.isNotEmpty()) ThinkChip(parts)
                if (clean.isNotEmpty()) MarkdownText(clean)
                if (hasZip) ProjectCard(m, { onZip(m) }, { onFile(m) }, onFix)
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
                    m.notes?.split("\n")?.filter { it.isNotBlank() && !it.startsWith("⚠") && !it.startsWith("🔧") && !it.startsWith(com.freellm.chat.domain.CutMark.PREFIX) }?.take(1)?.forEach { NoteChip(it) }
                }
                if (canSwitch && com.freellm.chat.domain.CutMark.has(m.notes)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.reply_cut_off),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.secondary
                        )
                        TextButton(onClick = onContinue) { Text(stringResource(R.string.continue_reply)) }
                    }
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
internal fun ModelBadge(name: String) {
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
internal fun SourcesRow(sources: List<StoredSource>, modifier: Modifier = Modifier) {
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
internal fun PendingCard(onRetry: () -> Unit, onPick: () -> Unit) {
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
internal fun StreamItem(
    s: StreamUi,
    onRetry: () -> Unit,
    onPick: () -> Unit,
    onTryOther: () -> Unit,
    onDismiss: () -> Unit,
    onNextModel: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val parts = remember(s.text) { com.freellm.chat.domain.ThinkSplit.split(s.text) }
        val waiting = s.active && s.error == null && parts.answer.isEmpty()
        val stalledState = remember(s.target?.key) { mutableStateOf(false) }
        LaunchedEffect(s.target?.key, waiting) {
            stalledState.value = false
            if (waiting) {
                kotlinx.coroutines.delay(6_000L)
                stalledState.value = true
            }
        }
        val lastThought = remember(parts.thoughts) { parts.thoughts.lines().lastOrNull { it.isNotBlank() }?.trim() }
        if (parts.thoughts.isNotEmpty()) ThinkChip(parts)
        if (parts.answer.isNotEmpty()) {
            MarkdownText(com.freellm.chat.domain.CodeFiles.stripCommand(parts.answer), cursor = s.active)
        } else if (s.active) {
            val compact = s.status != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarBurst(size = if (compact) 11.dp else 20.dp, spinning = true)
                Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                val showThought = !compact && !lastThought.isNullOrEmpty()
                Text(
                    s.status ?: (if (showThought) lastThought else null) ?: stringResource(R.string.typing),
                    color = if (compact) cs.onSurfaceVariant.copy(alpha = 0.55f) else if (showThought) cs.onSurfaceVariant.copy(alpha = 0.6f) else cs.onSurfaceVariant,
                    fontSize = if (compact) 9.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    lineHeight = if (compact) 11.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = if (compact || showThought) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
                if (!compact) TypingDots()
            }
            if (!compact && lastThought.isNullOrEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(Corners.sm))
                        .shimmer()
                )
            }
            if (stalledState.value) {
                TextButton(onClick = onNextModel) { Text(stringResource(R.string.chat_try_other)) }
            }
            val pr = s.progress
            if (pr != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { pr.coerceIn(0f, 1f) },
                        modifier = Modifier.size(if (compact) 9.dp else 22.dp),
                        color = if (compact) cs.primary.copy(alpha = 0.6f) else cs.primary,
                        trackColor = cs.surfaceContainerHigh,
                        strokeWidth = if (compact) 1.dp else 3.dp
                    )
                    Spacer(Modifier.width(if (compact) 5.dp else 10.dp))
                    Text(
                        (pr.coerceIn(0f, 1f) * 100).toInt().toString() + "%",
                        fontSize = if (compact) 8.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                        lineHeight = if (compact) 10.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (compact) cs.onSurfaceVariant.copy(alpha = 0.55f) else cs.onSurfaceVariant
                    )
                }
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
