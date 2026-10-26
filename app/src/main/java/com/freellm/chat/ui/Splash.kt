package com.freellm.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val SplashBg = Color(0xFF05060A)
private val RayA = Color(0xFF2F7BFF)
private val RayB = Color(0xFF7FB0FF)

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

/** Black screen where the blue spark blooms ray by ray, turns and settles, then fades into the app. */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val bloom = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { bloom.animateTo(1f, tween(1250, easing = LinearEasing)) }
            launch { spin.animateTo(1f, tween(1700, easing = FastOutSlowInEasing)) }
            launch {
                delay(650)
                title.animateTo(1f, tween(500))
            }
        }
        delay(200)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        rotationZ = -70f * (1f - spin.value)
                        val s = 1f + 0.35f * exit.value
                        scaleX = s
                        scaleY = s
                    }
            ) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val n = 12
                drawCircle(
                    Brush.radialGradient(
                        listOf(RayA.copy(alpha = 0.28f * bloom.value), Color.Transparent),
                        center = c,
                        radius = r * 1.15f
                    ),
                    radius = r * 1.15f,
                    center = c
                )
                for (i in 0 until n) {
                    val start = (i / n.toFloat()) * 0.55f
                    val local = ((bloom.value - start) / 0.45f).coerceIn(0f, 1f)
                    if (local <= 0f) continue
                    val a = Math.toRadians(i * 360.0 / n - 90.0)
                    val target = r * (if (i % 2 == 0) 1f else 0.82f)
                    val outer = target * easeOutBack(local)
                    val inner = r * 0.2f
                    drawLine(
                        lerp(RayB, RayA, if (i % 2 == 0) 1f else 0.35f).copy(alpha = local),
                        Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                        Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
                        strokeWidth = r * 0.17f,
                        cap = StrokeCap.Round
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "FreeLLM",
                Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 14f
                },
                color = Color(0xFFEDEDED),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
