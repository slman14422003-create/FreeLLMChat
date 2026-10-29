package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freellm.chat.R
import com.freellm.chat.domain.ThinkParts
import com.freellm.chat.domain.ThinkSplit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkChip(parts: ThinkParts) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val steps = remember(parts.thoughts) { ThinkSplit.steps(parts.thoughts) }
    Row(
        Modifier
            .clip(RoundedCornerShape(Corners.chip))
            .background(cs.surfaceVariant)
            .clickable { open = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        Text(
            if (parts.thinking) stringResource(R.string.think_active) else stringResource(R.string.think_done, steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant
        )
    }
    if (open) {
        val dir = detectDirection(parts.thoughts) ?: LocalLayoutDirection.current
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { open = false }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.think_close))
                        }
                        Text(
                            stringResource(R.string.think_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.lg)
                    ) {
                        itemsIndexed(steps) { i, text ->
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(7.dp))
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(cs.outline))
                                    if (i < steps.lastIndex || parts.thinking) {
                                        Box(Modifier.width(1.dp).weight(1f).background(cs.outlineVariant))
                                    }
                                }
                                Text(
                                    text,
                                    Modifier.weight(1f).padding(bottom = Spacing.lg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (i == steps.lastIndex && parts.thinking) cs.onSurface else cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
