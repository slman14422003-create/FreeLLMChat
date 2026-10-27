package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freellm.chat.R
import com.freellm.chat.domain.CompareSide
import com.freellm.chat.domain.Merger
import com.freellm.chat.domain.SideState

@Composable
fun CompareScreen(
    ui: CompareUi,
    mergeEnabled: Boolean,
    onChoose: (Boolean) -> Unit,
    onMerge: () -> Unit,
    onChooseMerged: () -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.xl, end = Spacing.sm, top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.compare_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, stringResource(R.string.compare_close))
                    }
                }
                Text(
                    ui.question,
                    Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 600.dp
                    if (wide) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            SideCard(
                                ui.result.a, true, { onChoose(true) },
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .appear(0)
                            )
                            SideCard(
                                ui.result.b, true, { onChoose(false) },
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .appear(1)
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            SideCard(ui.result.a, false, { onChoose(true) }, Modifier.fillMaxWidth().appear(0))
                            SideCard(ui.result.b, false, { onChoose(false) }, Modifier.fillMaxWidth().appear(1))
                        }
                    }
                }
                if (mergeEnabled && Merger.canMerge(ui.result)) {
                    MergeArea(ui, onMerge, onChooseMerged)
                }
            }
        }
    }
}

@Composable
private fun SideCard(side: CompareSide, scrollInside: Boolean, onChoose: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(Corners.card))
            .background(cs.surfaceContainerHigh)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    side.target.model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    side.target.providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            val label = when (side.state) {
                SideState.RUNNING -> stringResource(R.string.compare_running)
                SideState.DONE -> stringResource(R.string.compare_done)
                SideState.FAILED -> stringResource(R.string.compare_failed)
            }
            val color = when (side.state) {
                SideState.RUNNING -> cs.onSurfaceVariant
                SideState.DONE -> cs.tertiary
                SideState.FAILED -> cs.error
            }
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
        val body = if (scrollInside) {
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(body) {
            when {
                side.text.isNotBlank() -> MarkdownText(side.text)
                side.state == SideState.FAILED -> Text(side.error.orEmpty(), color = cs.error)
                else -> TypingDots()
            }
        }
        Button(
            onClick = onChoose,
            enabled = side.state == SideState.DONE,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.compare_choose)) }
    }
}

@Composable
private fun MergeArea(ui: CompareUi, onMerge: () -> Unit, onChooseMerged: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m = ui.merge
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        when {
            m == null -> OutlinedButton(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.compare_merge))
            }
            m.busy -> Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compare_merging), color = cs.onSurfaceVariant)
            }
            m.failed -> {
                Text(stringResource(R.string.compare_merge_failed), color = cs.error)
                OutlinedButton(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compare_merge))
                }
            }
            else -> Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.card))
                    .background(cs.surfaceContainerHigh)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(stringResource(R.string.compare_merged_title), style = MaterialTheme.typography.titleMedium)
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) { MarkdownText(m.text.orEmpty()) }
                Button(onClick = onChooseMerged, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compare_use_merged))
                }
            }
        }
    }
}
