@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Warning
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

enum class PickPurpose { MODE, MODEL, CONTINUE, RETRY, REGENERATE }

private fun ctxLabel(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1000 -> "${n / 1000}K"
    else -> "$n"
}

/**
 * Bottom sheet used for every "pick a model" moment: choosing the chat model, handing a
 * half-finished reply to another model, or re-running a saved request.
 * [onPick] receives a selection code: AUTO / FAST / SMART / CODE / PIN:<providerId/modelId>.
 */
@Composable
fun ModelSheet(
    purpose: PickPurpose,
    selection: String,
    targets: List<Target>,
    health: (Target) -> ModelHealth,
    onPick: (String) -> Unit,
    onPrivacy: () -> Unit,
    onProviders: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    val now = System.currentTimeMillis()

    val title = when (purpose) {
        PickPurpose.MODE -> R.string.pick_title_mode
        PickPurpose.MODEL -> R.string.pick_title_select
        PickPurpose.CONTINUE -> R.string.pick_title_continue
        PickPurpose.RETRY -> R.string.pick_title_retry
        PickPurpose.REGENERATE -> R.string.pick_title_regen
    }
    val showModes = purpose != PickPurpose.MODEL
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = cs.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(title),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge
            )
            if (purpose != PickPurpose.MODE && purpose != PickPurpose.MODEL) {
                Text(
                    stringResource(R.string.request_saved),
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            if (showModels) Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
            }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                if (showModes && q.isEmpty()) {
                    item(key = "modes_label") { GroupLabel(stringResource(R.string.modes_label)) }
                    items(modes, key = { "mode_${it.first}" }) { (code, label) ->
                        PickRow(
                            title = stringResource(label),
                            subtitle = null,
                            selected = selection == code,
                            enabled = true,
                            trailing = null,
                            onClick = { onPick(code) }
                        )
                    }
                }
                if (showModels && targets.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.no_provider_hint), color = cs.onSurfaceVariant)
                            TextButton(onClick = onProviders) { Text(stringResource(R.string.setup_provider)) }
                        }
                    }
                }
                grouped.forEach { (provider, list) ->
                    item(key = "g_$provider") { GroupLabel(provider) }
                    items(list, key = { it.key }) { t ->
                        val h = health(t)
                        val dead = h is ModelHealth.Dead || h is ModelHealth.ProviderDown
                        val tags = buildList {
                            add(ctxLabel(t.model.contextWindow))
                            if (t.model.vision) add(stringResource(R.string.tag_vision))
                            if (t.model.reasoning) add(stringResource(R.string.tag_reasoning))
                        }.joinToString(" · ")
                        val status: String? = when (h) {
                            is ModelHealth.Cooling ->
                                stringResource(R.string.status_cooling, maxOf(0L, (h.untilMs - now) / 1000).toInt())
                            is ModelHealth.Dead -> stringResource(R.string.status_dead)
                            is ModelHealth.ProviderDown -> stringResource(R.string.status_disabled)
                            else -> null
                        }
                        PickRow(
                            title = t.model.displayName,
                            subtitle = tags,
                            selected = selection == "PIN:${t.key}",
                            enabled = !dead,
                            trailing = {
                                if (status != null) {
                                    Text(status, fontSize = 12.sp, color = if (dead) cs.error else cs.secondary)
                                }
                                if (t.model.trainsOnData) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        stringResource(R.string.trains_title),
                                        Modifier
                                            .padding(start = 8.dp)
                                            .size(18.dp)
                                            .clickable(onClick = onPrivacy),
                                        tint = cs.secondary
                                    )
                                }
                            },
                            onClick = { onPick("PIN:${t.key}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PickRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) cs.surfaceContainerHigh else cs.background)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        trailing?.invoke()
        if (selected) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = cs.tertiary)
        else Spacer(Modifier.height(0.dp))
    }
}
