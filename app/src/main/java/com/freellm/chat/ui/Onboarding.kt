package com.freellm.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freellm.chat.R
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val PAGES = 5

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val p = pager.currentPage + pager.currentPageOffsetFraction
            val c1 = Offset(size.width * (0.1f + 0.2f * p), size.height * 0.2f)
            val c2 = Offset(size.width * (0.95f - 0.15f * p), size.height * 0.9f)
            drawCircle(
                Brush.radialGradient(listOf(Accent.copy(alpha = 0.2f), Color.Transparent), center = c1, radius = size.width),
                radius = size.width, center = c1
            )
            drawCircle(
                Brush.radialGradient(listOf(ClaudeOrange.copy(alpha = 0.12f), Color.Transparent), center = c2, radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = c2
            )
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(visible = !last) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.ob_skip)) }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val o = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - 0.85f * o
                            scaleX = 1f - 0.1f * o
                            scaleY = 1f - 0.1f * o
                        }
                ) { OnboardingPage(page, pager.currentPage == page) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    repeat(PAGES) { i ->
                        val sel = pager.currentPage == i
                        val w by animateDpAsState(
                            if (sel) 26.dp else 8.dp,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "dotW"
                        )
                        val col by animateColorAsState(if (sel) Accent else cs.outline, label = "dotC")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(col))
                    }
                }
                Button(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.height(52.dp).widthIn(min = 120.dp),
                    shape = CircleShape
                ) {
                    Crossfade(targetState = last, label = "obBtn") { l ->
                        Text(stringResource(if (l) R.string.start_now else R.string.ob_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int, active: Boolean) {
    val cs = MaterialTheme.colorScheme
    val title = stringResource(
        when (page) {
            0 -> R.string.ob0_title
            1 -> R.string.ob1_title
            2 -> R.string.ob2_title
            3 -> R.string.ob3_title
            else -> R.string.ob4_title
        }
    )
    val body = stringResource(
        when (page) {
            0 -> R.string.ob0_body
            1 -> R.string.ob1_body
            2 -> R.string.ob2_body
            3 -> R.string.ob3_body
            else -> R.string.ob4_body
        }
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 260.dp)
                .appearWhen(active, 0),
            contentAlignment = Alignment.Center
        ) {
            val art = Modifier.fillMaxSize()
            when (page) {
                0 -> BrandRow(
                    markSize = 92.dp,
                    devName = stringResource(R.string.dev_name),
                    devRole = stringResource(R.string.dev_role),
                    aiName = stringResource(R.string.claude_name),
                    aiRole = stringResource(R.string.claude_role)
                )
                1 -> RoutingArt(art)
                2 -> FailoverArt(art)
                3 -> KeysArt(art)
                else -> PrivacyArt(art)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            Modifier.appearWhen(active, 1),
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            body,
            Modifier.appearWhen(active, 2),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }
}
