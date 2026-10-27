package com.freellm.chat.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.domain.BrainEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun BrainScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val brain = app.brainStore.brain
    var entries by remember { mutableStateOf(brain.all().sortedByDescending { it.createdAt }) }
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<BrainEntry?>(null) }
    var facts by remember { mutableStateOf("") }
    var factsLoaded by remember { mutableStateOf(false) }

    fun refresh() {
        entries = brain.all().sortedByDescending { it.createdAt }
    }

    LaunchedEffect(Unit) {
        facts = app.settings.facts.first()
        factsLoaded = true
    }
    LaunchedEffect(facts) {
        if (!factsLoaded) return@LaunchedEffect
        delay(500)
        app.settings.setFacts(facts)
    }

    val q = filter.trim()
    val shown = if (q.isEmpty()) entries else entries.filter {
        it.question.contains(q, ignoreCase = true) || it.answer.contains(q, ignoreCase = true)
    }

    val e = editing
    if (e != null) {
        var question by remember(e.id) { mutableStateOf(e.question) }
        var answer by remember(e.id) { mutableStateOf(e.answer) }
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    brain.update(e.id, question, answer)
                    scope.launch { app.brainStore.save() }
                    refresh()
                    editing = null
                }, enabled = question.trim().length >= 3 && answer.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        brain.remove(e.id)
                        scope.launch { app.brainStore.save() }
                        refresh()
                        editing = null
                    }) { Text(stringResource(R.string.delete), color = cs.error) }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
            title = { Text(stringResource(R.string.brain_entry)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(question, { question = it }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(answer, { answer = it }, maxLines = 8, modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { AppTopBar(stringResource(R.string.brain_manage), onBack) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(key = "art") { BrainArt(Modifier.fillMaxWidth().height(130.dp).appear(0)) }
            item(key = "facts") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.personal_facts), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        facts,
                        { facts = it.take(1500) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        placeholder = { Text(stringResource(R.string.personal_facts_hint)) }
                    )
                    OutlinedTextField(
                        filter,
                        { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_brain)) }
                    )
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.brain_empty), color = cs.onSurfaceVariant) }
            }
            items(shown, key = { it.id }) { en ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = en }
                        .padding(vertical = Spacing.xs)
                ) {
                    Text(en.question, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        en.answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider(Modifier.padding(top = Spacing.sm))
                }
            }
        }
    }
}
