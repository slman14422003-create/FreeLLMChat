@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freellm.chat.R
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.Target

enum class PickPurpose { MODE, MODEL, CONTINUE, RETRY, REGENERATE, COMPARE }

private val Palette = listOf(
    Color(0xFF2F7BFF), Color(0xFFD97757), Color(0xFF34C759), Color(0xFFA855F7),
    Color(0xFFF59E0B), Color(0xFF14B8A6), Color(0xFFEC4899), Color(0xFF6366F1)
)

private fun tintOf(name: String): Color = Palette[(name.hashCode() and 0x7fffffff) % Palette.size]

private fun ctxLabel(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1000 -> "${n / 1000}K"
    else -> "$n"
}

private fun modeIcon(code: String): ImageVector = when (code) {
    "ADAPT" -> Icons.Outlined.AutoAwesome
    "FAST" -> Icons.Outlined.Bolt
    "SMART" -> Icons.Outlined.Psychology
    "CODE" -> Icons.Outlined.Code
    else -> Icons.Outlined.Shuffle
}

private data class Tag(val text: String, val icon: ImageVector?)

@Composable
fun ModelSheet(
    purpose: PickPurpose,
    selection: String,
    targets: List<Target>,
    health: (Target) -> ModelHealth,
    latency: (Target) -> Long? = { null },
    onPick: (String) -> Unit,
    onCompare: (String, String) -> Unit = { _, _ -> },
    onPrivacy: () -> Unit,
    onProviders: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(listOf<String>()) }
    val now = System.currentTimeMillis()

    val title = when (purpose) {
        PickPurpose.MODE -> R.string.pick_title_mode
        PickPurpose.MODEL -> R.string.pick_title_select
        PickPurpose.CONTINUE -> R.string.pick_title_continue
        PickPurpose.RETRY -> R.string.pick_title_retry
        PickPurpose.REGENERATE -> R.string.pick_title_regen
        PickPurpose.COMPARE -> R.string.pick_title_compare
    }
    val comparing = purpose == PickPurpose.COMPARE
    val showModes = purpose != PickPurpose.MODEL && !comparing
    val showModels = purpose != PickPurpose.MODE
    val modes = if (purpose == PickPurpose.MODE) {
        listOf(
            "ADAPT" to R.string.mode_adaptive,
            "AUTO" to R.string.mode_auto,
            "FAST" to R.string.mode_fast,
            "SMART" to R.string.mode_smart,
            "CODE" to R.string.mode_code
        )
    } else {
        listOf("AUTO" to R.string.mode_auto)
    }
    val q = filter.trim()
    val visibleTargets = if (showModels) targets else emptyList()
    val shown = if (q.isEmpty()) visibleTargets else visibleTargets.filter {
        it.model.displayName.contains(q, ignoreCase = true) || it.providerName.contains(q, ignoreCase = true)
    }
    val grouped = shown.groupBy { it.providerName }
    val fastestKey = shown.mapNotNull { t -> latency(t)?.let { t.key to it } }.minByOrNull { it.second }?.first

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = cs.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = Spacing.xxl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(21.dp), tint = Accent) }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            }
            if (comparing) {
                Text(
                    stringResource(R.string.compare_pick_hint),
                    Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            } else if (purpose != PickPurpose.MODE && purpose != PickPurpose.MODEL) {
                Text(
                    stringResource(R.string.request_saved),
                    Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            if (showModels) Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    .clip(RoundedCornerShape(26.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (filter.isEmpty()) {
                        Text(stringResource(R.string.search_models), color = cs.onSurfaceVariant, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(cs.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filter.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, null,
                        Modifier.size(18.dp).clip(CircleShape).clickable { filter = "" },
                        tint = cs.onSurfaceVariant
                    )
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (showModes && q.isEmpty()) {
                    item(key = "modes_label") { GroupLabel(stringResource(R.string.modes_label), null, 0) }
                    items(modes, key = { "mode_${it.first}" }) { (code, label) ->
                        PickRow(
                            title = stringResource(label),
                            tags = emptyList(),
                            status = null,
                            statusBad = false,
                            selected = selection == code,
                            enabled = true,
                            leading = { IconBadge(modeIcon(code), Accent) },
                            trailing = null,
                            onClick = { onPick(code) }
                        )
                    }
                }
                if (showModels && targets.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(Spacing.xxl)) {
                            Text(stringResource(R.string.no_provider_hint), color = cs.onSurfaceVariant)
                            TextButton(onClick = onProviders) { Text(stringResource(R.string.setup_provider)) }
                        }
                    }
                }
                grouped.forEach { (provider, list) ->
                    item(key = "g_$provider") { GroupLabel(provider, tintOf(provider), list.size) }
                    items(list, key = { it.key }) { t ->
                        val h = health(t)
                        val dead = h is ModelHealth.Dead || h is ModelHealth.ProviderDown
                        val tags = buildList {
                            add(Tag(ctxLabel(t.model.contextWindow), Icons.Outlined.Memory))
                            if (t.model.vision) add(Tag(stringResource(R.string.tag_vision), Icons.Outlined.Visibility))
                            if (t.model.reasoning) add(Tag(stringResource(R.string.tag_reasoning), Icons.Outlined.Lightbulb))
                            latency(t)?.let { ms -> add(Tag("$ms ms", if (t.key == fastestKey) Icons.Outlined.Bolt else null)) }
                        }
                        val status: String? = when (h) {
                            is ModelHealth.Cooling ->
                                stringResource(R.string.status_cooling, maxOf(0L, (h.untilMs - now) / 1000).toInt())
                            is ModelHealth.Dead -> stringResource(R.string.status_dead)
                            is ModelHealth.ProviderDown -> stringResource(R.string.status_disabled)
                            else -> null
                        }
                        val trainsDesc = stringResource(R.string.trains_title)
                        PickRow(
                            title = t.model.displayName,
                            tags = tags,
                            status = status,
                            statusBad = dead,
                            selected = if (comparing) t.key in chosen else selection == "PIN:${t.key}",
                            enabled = !dead,
                            leading = { LetterBadge(t.model.displayName, tintOf(provider)) },
                            trailing = if (t.model.trainsOnData) {
                                { ExclamationBadge(trainsDesc, onPrivacy) }
                            } else null,
                            onClick = {
                                if (comparing) {
                                    chosen = if (t.key in chosen) chosen - t.key else (chosen + t.key).takeLast(2)
                                } else {
                                    onPick("PIN:${t.key}")
                                }
                            }
                        )
                    }
                }
            }
            if (comparing) {
                Button(
                    onClick = { if (chosen.size == 2) onCompare("PIN:${chosen[0]}", "PIN:${chosen[1]}") },
                    enabled = chosen.size == 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) { Text(stringResource(R.string.compare_start)) }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String, tint: Color?, count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tint != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
}

@Composable
private fun LetterBadge(name: String, tint: Color) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Text(letter, color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable
private fun ExclamationBadge(description: String, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.5.dp, tint, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Text("!", color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
}

@Composable
private fun TagChip(tag: Tag) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tag.icon != null) {
            Icon(tag.icon, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(3.dp))
        }
        Text(tag.text, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PickRow(
    title: String,
    tags: List<Tag>,
    status: String?,
    statusBad: Boolean,
    selected: Boolean,
    enabled: Boolean,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 3.dp)
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.12f) else cs.surfaceContainer)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Accent else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tags.isNotEmpty() || status != null) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    tags.forEach { TagChip(it) }
                    if (status != null) {
                        Text(
                            status,
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background((if (statusBad) cs.error else cs.secondary).copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = if (statusBad) cs.error else cs.secondary
                        )
                    }
                }
            }
        }
        trailing?.invoke()
        if (selected) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color.White) }
        }
    }
}
