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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.graphicsLayer
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
internal fun AttachItem(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
internal fun Composer(
    input: String,
    onInput: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    pending: List<Attachment>,
    importing: Boolean,
    onPickImage: () -> Unit,
    onCamera: () -> Unit = {},
    onPickFile: () -> Unit,
    onPickPdfImages: () -> Unit,
    onRemove: (Attachment) -> Unit,
    onQuick: (String) -> Unit,
    onImagesToPdf: () -> Unit,
    onPlus: () -> Unit,
    listening: Boolean = false,
    micLevel: Float = 0f,
    micStatic: Boolean = false,
    onMic: (() -> Unit)? = null,
    onMicCancel: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    var attachMenu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val glow = rememberInfiniteTransition(label = "glow")
    val pulse = glow.motionFloat(
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
                DropdownMenu(
                    expanded = attachMenu,
                    onDismissRequest = { attachMenu = false },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = cs.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    AttachItem(Icons.Outlined.CameraAlt, Color(0xFF2F7BFF), stringResource(R.string.attach_camera)) {
                        attachMenu = false
                        onCamera()
                    }
                    AttachItem(Icons.Outlined.Image, Color(0xFF34C759), stringResource(R.string.attach_photo)) {
                        attachMenu = false
                        onPickImage()
                    }
                    AttachItem(Icons.Outlined.Description, Color(0xFFD97757), stringResource(R.string.attach_file)) {
                        attachMenu = false
                        onPickFile()
                    }
                    AttachItem(Icons.Outlined.PictureAsPdf, Color(0xFFE5484D), stringResource(R.string.attach_pdf_images)) {
                        attachMenu = false
                        onPickPdfImages()
                    }
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
                listening -> 3
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
                    1 -> CircleButton(
                        onClick = { if (!importing) onSend() },
                        modifier = Modifier.alpha(if (importing) 0.4f else 1f),
                        background = cs.primary
                    ) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.send), Modifier.size(22.dp), tint = cs.onPrimary)
                    }
                    3 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(onClick = onMicCancel, background = cs.surfaceContainerHighest) {
                            Icon(Icons.Filled.Close, stringResource(R.string.mic_cancel), Modifier.size(20.dp), tint = cs.onSurface)
                        }
                        Spacer(Modifier.width(8.dp))
                        val scale = if (micStatic) 1f else 1f + micLevel.coerceIn(0f, 1f) * 0.3f
                        CircleButton(
                            onClick = { onMic?.invoke() },
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            background = cs.primary
                        ) {
                            Icon(Icons.Filled.Mic, stringResource(R.string.mic_stop), Modifier.size(22.dp), tint = cs.onPrimary)
                        }
                    }
                    else -> if (onMic != null) {
                        CircleButton(onClick = onMic, background = cs.surfaceContainerHighest) {
                            Icon(Icons.Filled.Mic, stringResource(R.string.mic_start), Modifier.size(22.dp), tint = cs.onSurface)
                        }
                    }
                }
            }
        }
        }
    }
}

internal class PdfJob(val text: String?, val images: List<Attachment>)

@Composable
internal fun QuickChip(label: String, onClick: () -> Unit) {
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
internal fun AttachThumb(a: Attachment, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
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
internal fun PendingChip(a: Attachment, onRemove: () -> Unit) {
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
