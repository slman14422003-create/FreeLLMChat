package com.freellm.chat.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freellm.chat.R
import com.freellm.chat.data.db.MessageEntity
import com.freellm.chat.domain.CodeFiles
import com.freellm.chat.domain.ProjectCheck
import com.freellm.chat.domain.ProjectReview
import com.freellm.chat.ui.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun sizeText(chars: Int): String {
    val kb = chars / 1024.0
    return if (kb < 1) "$chars B" else if (kb < 1024) String.format(java.util.Locale.US, "%.1f KB", kb) else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
}

@Composable
internal fun ProjectCard(m: MessageEntity, onZip: () -> Unit, onFile: () -> Unit, onFix: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tool = m.notes?.contains("📁") == true
    val review by produceState<ProjectReview?>(null, m.content) {
        value = withContext(Dispatchers.Default) { ProjectCheck.review(m.content) }
    }
    var open by remember { mutableStateOf(false) }
    val files = review?.files.orEmpty()
    val single = !tool && files.size == 1 && CodeFiles.zipCommand(m.content) == null
    val name = if (tool) "project" else CodeFiles.projectName(files)
    val issues = if (tool) emptyList() else review?.issues.orEmpty()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(if (single) Icons.Outlined.Description else Icons.Outlined.Folder, null, tint = cs.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (single) files[0].path else "$name.zip",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (files.isNotEmpty()) {
                        Text(
                            stringResource(R.string.project_card_files, files.size, sizeText(files.sumOf { it.content.length })),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            when {
                review == null && !tool -> Text(stringResource(R.string.project_card_checking), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                issues.isEmpty() -> Text(stringResource(R.string.project_card_complete), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                else -> {
                    Text(stringResource(R.string.project_card_incomplete, issues.size), style = MaterialTheme.typography.labelMedium, color = cs.error)
                    Text(
                        ProjectCheck.summary(issues, 5),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (open && files.isNotEmpty()) {
                Text(
                    files.joinToString("\n") { it.path },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (single) onFile() else onZip() }) {
                    Text(stringResource(if (single) R.string.project_card_file else R.string.project_card_zip))
                }
                if (issues.isNotEmpty()) {
                    OutlinedButton(onClick = onFix) { Text(stringResource(R.string.project_card_fix)) }
                }
                if (files.size > 1) {
                    TextButton(onClick = { open = !open }) {
                        Text(stringResource(if (open) R.string.project_card_hide else R.string.project_card_show))
                    }
                }
            }
        }
    }
}
