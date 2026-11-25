package com.freellm.chat.ui.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freellm.chat.ui.LiveColors
import com.freellm.chat.ui.LocalReduceMotion
import com.freellm.chat.ui.Motion

@Composable
fun Orb(state: LiveState, level: Float, modifier: Modifier = Modifier, size: Dp = 260.dp) {
    val reduce = LocalReduceMotion.current
    val target = when (state) {
        LiveState.Thinking -> LiveColors.Thinking
        LiveState.Speaking -> LiveColors.Speaking
        else -> LiveColors.Listening
    }
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = if (reduce) snap() else tween(Motion.Slow),
        label = "orbColor"
    )
    val lvl by animateFloatAsState(
        targetValue = if (reduce) 0f else level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "orbLevel"
    )
    val infinite = rememberInfiniteTransition(label = "orb")
    val breath = if (reduce) 0f else infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath"
    ).value
    val sweep = if (reduce) 0f else infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep"
    ).value
    val ripple = if (reduce) 0f else infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "ripple"
    ).value

    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f * 0.42f
        when (state) {
            LiveState.Thinking -> {
                drawCircle(color.copy(alpha = 0.16f), r * 1.25f, c)
                drawCircle(color.copy(alpha = 0.55f), r, c)
                rotate(sweep, c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, color.copy(alpha = 0.9f), Color.Transparent),
                            c
                        ),
                        radius = r * 1.5f,
                        center = c,
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
            }
            LiveState.Speaking -> {
                if (!reduce) {
                    for (k in 0..2) {
                        val p = (ripple + k / 3f) % 1f
                        drawCircle(
                            color = color.copy(alpha = (1f - p) * 0.5f),
                            radius = r * (1f + 0.9f * p),
                            center = c,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                drawCircle(color.copy(alpha = 0.18f), r * 1.2f, c)
                drawCircle(color.copy(alpha = 0.6f), r, c)
            }
            else -> {
                val idle = if (state == LiveState.Idle) 0.5f else 1f
                drawCircle(color.copy(alpha = 0.16f * idle), r * (1.3f + 0.08f * breath + 0.4f * lvl), c)
                drawCircle(color.copy(alpha = 0.6f * idle), r * (1f + 0.05f * breath + 0.18f * lvl), c)
            }
        }
    }
}
