package com.freellm.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.State
import kotlin.math.PI
import kotlin.math.sin
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
private fun rememberLoop(durationMs: Int, reverse: Boolean = false): State<Float> {
    val t = rememberInfiniteTransition(label = "obLoop")
    return t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(durationMs, easing = LinearEasing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart),
        label = "obLoopValue"
    )
}

@Composable
private fun FloatingDots(modifier: Modifier = Modifier) {
    val t = rememberLoop(16000)
    Canvas(modifier) {
        val n = 16
        for (i in 0 until n) {
            val seed = (i * 0.61803f) % 1f
            val rise = (t.value + i / n.toFloat()) % 1f
            val x = size.width * (seed + 0.03f * sin(2f * PI.toFloat() * (t.value * 2f + i)))
            val y = size.height * (1.05f - rise * 1.1f)
            val fade = sin(PI.toFloat() * rise).coerceIn(0f, 1f)
            val col = if (i % 3 == 0) ClaudeOrange else Accent
            drawCircle(col.copy(alpha = 0.22f * fade), radius = (3f + (i % 4) * 1.6f) * density, center = Offset(x, y))
        }
    }
}

@Composable
private fun PulseRings(modifier: Modifier = Modifier) {
    val t = rememberLoop(3600)
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension / 2f
        for (k in 0 until 3) {
            val f = (t.value + k / 3f) % 1f
            drawCircle(
                Accent.copy(alpha = 0.22f * (1f - f)),
                radius = base * (0.45f + 0.55f * f),
                center = c,
                style = Stroke(width = 2f * density)
            )
        }
    }
}

@Composable
fun OnboardingScreen(ready: Boolean = true, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    val enter by animateFloatAsState(if (ready) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "obEnter")
    val spin = rememberLoop(26000)

    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val p = pager.currentPage + pager.currentPageOffsetFraction
            val c1 = Offset(size.width * (0.1f + 0.2f * p), size.height * 0.2f)
            val c2 = Offset(size.width * (0.95f - 0.15f * p), size.height * 0.9f)
            drawCircle(
                Brush.radialGradient(listOf(Accent.copy(alpha = 0.2f * enter), Color.Transparent), center = c1, radius = size.width),
                radius = size.width, center = c1
            )
            drawCircle(
                Brush.radialGradient(listOf(ClaudeOrange.copy(alpha = 0.12f * enter), Color.Transparent), center = c2, radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = c2
            )
        }
        FloatingDots(Modifier.fillMaxSize().graphicsLayer { alpha = enter })
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                SparkMark(
                    44.dp,
                    Modifier.graphicsLayer {
                        val pos = pager.currentPage + pager.currentPageOffsetFraction
                        val e = 1f - enter
                        rotationZ = spin.value * 360f + 36f * pos - 70f * e
                        translationY = e * h * 0.36f
                        val s = 1f + 1.6f * e
                        scaleX = s
                        scaleY = s
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = !last && ready) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.ob_skip)) }
                    }
                }
            }
            HorizontalPager(
                pager,
                Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 48.dp.toPx()
                }
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val o = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - 0.85f * o
                            scaleX = 1f - 0.1f * o
                            scaleY = 1f - 0.1f * o
                        }
                ) {
                    OnboardingPage(page, pager.currentPage == page && ready) { (pager.currentPage - page) + pager.currentPageOffsetFraction }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
                    .graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * 32.dp.toPx()
                    },
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
                val pulse by rememberInfiniteTransition(label = "obBtn").animateFloat(
                    1f, if (last) 1.06f else 1f,
                    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "obBtnPulse"
                )
                Button(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.height(52.dp).widthIn(min = 120.dp).graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
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
private fun OnboardingPage(page: Int, active: Boolean, offset: () -> Float) {
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
    val bob = rememberLoop(2600, reverse = true)
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
                .graphicsLayer { translationX = offset() * 180f }
                .appearWhen(active, 0),
            contentAlignment = Alignment.Center
        ) {
            PulseRings(Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val b = (bob.value - 0.5f) * 2f
                        translationY = b * 7.dp.toPx()
                        val sc = 1f + 0.015f * b
                        scaleX = sc
                        scaleY = sc
                    },
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
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            Modifier.graphicsLayer { translationX = offset() * -60f }.appearWhen(active, 1),
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            body,
            Modifier.graphicsLayer { translationX = offset() * -30f }.appearWhen(active, 2),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }
}
