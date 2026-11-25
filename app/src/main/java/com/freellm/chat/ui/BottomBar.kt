package com.freellm.chat.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.freellm.chat.R

@Composable
fun BottomBar(route: String, onSelect: (String) -> Unit) {
    val selected = if (route == "settings") 2 else 1
    BoxWithConstraints(Modifier.fillMaxWidth().background(surfaceLow()).navigationBarsPadding()) {
        val item = maxWidth / 3
        val x by animateDpAsState(item * selected + (item - 64.dp) / 2, spring(), label = "pill")
        Box(
            Modifier.offset(x = x, y = 8.dp).width(64.dp).height(32.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf("chat", "providers", "settings").forEachIndexed { i, target ->
                val on = i == selected
                val label = stringResource(listOf(R.string.nav_chat, R.string.nav_models, R.string.settings)[i])
                val icon = when (i) {
                    0 -> if (on) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline
                    1 -> if (on) Icons.Filled.Memory else Icons.Outlined.Memory
                    else -> if (on) Icons.Filled.Settings else Icons.Outlined.Settings
                }
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp)
                        .selectable(selected = on, role = Role.Tab) { onSelect(target) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
