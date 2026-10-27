package com.freellm.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset as GeoOffset
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.freellm.chat.R
import kotlin.math.cos
import kotlin.math.sin

class VmFactory<T : ViewModel>(private val block: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = block() as V
}

fun detectDirection(text: String): LayoutDirection? {
    for (ch in text) {
        if (ch.isLetter()) {
            val c = ch.code
            val rtl = c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF
            return if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        }
    }
    return null
}

/** Round, tappable icon container (Claude's "+" / back buttons). */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .bounceClick(onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Click with a springy press-down scale (reads state in the draw phase only). */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s = animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = s.value
            scaleY = s.value
        }
        .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
}

fun Modifier.appear(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 10) * Motion.Stagger)
        a.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 170f))
    }
    graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 30.dp.toPx()
        val sc = 0.95f + 0.05f * v
        scaleX = sc
        scaleY = sc
    }
}

fun Modifier.appearWhen(active: Boolean, index: Int = 0): Modifier = composed {
    val a = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (active) 560 else 200,
            delayMillis = if (active) 120 + index * 110 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "appearWhen"
    )
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 26.dp.toPx()
    }
}

/** Bouncy pop for a freshly sent message. */
fun Modifier.popIn(enabled: Boolean): Modifier = composed {
    val a = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) a.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 360f))
    }
    graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        val sc = 0.85f + 0.15f * v
        scaleX = sc
        scaleY = sc
        translationY = (1f - v) * 30.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
}

/** Three bouncing dots shown while the model is thinking. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = Accent) {
    val t = rememberInfiniteTransition(label = "dots")
    val phases = (0 until 3).map { i ->
        t.animateFloat(
            0f, 1f,
            infiniteRepeatable(
                tween(Motion.Slow, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 160)
            ),
            label = "dot$i"
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        phases.forEach { p ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationY = -6.dp.toPx() * p.value
                        alpha = 0.45f + 0.55f * p.value
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Small living dot: breathes when idle, pulses fast with a halo while the model answers. */
@Composable
fun LiveOrb(active: Boolean, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val p = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (active) 750 else 2400, easing = LinearEasing), RepeatMode.Restart),
        label = "orbP"
    )
    Canvas(modifier.size(size * 2.4f)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = size.toPx() / 2f
        val v = p.value
        if (active) {
            drawCircle(Accent.copy(alpha = 0.5f * (1f - v)), radius = r * (1f + 1.4f * v), center = c)
        }
        val breathe = if (active) 1f else 0.85f + 0.15f * sin(v * 2f * Math.PI.toFloat())
        drawCircle(Accent, radius = r * breathe, center = c)
    }
}

/** Claude-style sub-screen header: round back button, centered serif title. */
@Composable
fun AppTopBar(title: String, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .heightIn(min = 48.dp)
    ) {
        CircleButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart), size = 44.dp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.size(22.dp))
        }
        Text(
            title,
            Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Claude's two-line menu glyph (long line over a short one). */
@Composable
fun MenuGlyph(color: Color = MaterialTheme.colorScheme.onBackground) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val stroke = 2.2.dp.toPx()
        val longLen = w * 0.92f
        val shortLen = w * 0.58f
        fun line(y: Float, len: Float) {
            val x0 = if (rtl) w - len else 0f
            drawLine(color, Offset(x0, y), Offset(x0 + len, y), stroke, StrokeCap.Round)
        }
        line(size.height * 0.36f, longLen)
        line(size.height * 0.64f, shortLen)
    }
}

/** Radial burst mark (sparkle in the accent colour): spins while busy, breathes and sways when idle. */
@Composable
fun StarBurst(size: Dp, modifier: Modifier = Modifier, color: Color = Accent, spinning: Boolean = false) {
    val t = rememberInfiniteTransition(label = "burst")
    val angle = t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (spinning) 5200 else 14000, easing = LinearEasing), RepeatMode.Restart),
        label = "burstAngle"
    )
    val breathe = t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "burstScale"
    )
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = if (spinning) angle.value else angle.value * 0.5f
                val sc = if (spinning) 1f else breathe.value
                scaleX = sc
                scaleY = sc
            }
    ) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val n = 12
        for (i in 0 until n) {
            val a = Math.toRadians(i * 360.0 / n)
            val outer = r * (if (i % 2 == 0) 1f else 0.82f)
            val inner = r * 0.2f
            drawLine(
                color,
                Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
                strokeWidth = r * 0.17f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Rounded block of settings tiles separated by a thin gap, as in Claude's settings. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.card)),
        verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
        content = content
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = Spacing.sm, top = 22.dp, bottom = Spacing.sm),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
