package com.freellm.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val ClaudeOrange = Color(0xFFD97757)
val DevViolet = Color(0xFF8B5CF6)
val DevGold = Color(0xFFFFC857)

private val RayLengths = floatArrayOf(1f, 0.7f, 0.92f, 0.58f, 1f, 0.76f, 0.95f, 0.62f, 0.88f, 0.72f, 0.98f, 0.64f)

@Composable
fun ClaudeMark(size: Dp, modifier: Modifier = Modifier, color: Color = ClaudeOrange) {
    val t = rememberInfiniteTransition(label = "claudeMark")
    val spin = t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "claudeSpin"
    )
    val pulse = t.animateFloat(
        0.93f, 1.07f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "claudePulse"
    )
    val grow = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "claudeGrow"
    )
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = spin.value
                scaleX = pulse.value
                scaleY = pulse.value
            }
    ) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.26f), Color.Transparent), center = c, radius = r * 1.25f),
            radius = r * 1.25f,
            center = c
        )
        val n = RayLengths.size
        for (i in 0 until n) {
            val a = Math.toRadians(i * 360.0 / n - 90.0)
            val wave = 0.9f + 0.1f * sin((grow.value * 2f * Math.PI + i).toFloat())
            val outer = r * RayLengths[i] * wave
            val inner = r * 0.14f
            drawLine(
                color,
                Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color, radius = r * 0.11f, center = c)
    }
}

@Composable
fun DevMark(size: Dp, modifier: Modifier = Modifier) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "devMark")
    val sweep = t.animateFloat(
        -0.4f, 1.4f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "devSweep"
    )
    val glow = t.animateFloat(
        0.25f, 0.6f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "devGlow"
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = CornerRadius(w * 0.28f, w * 0.28f)
        drawRoundRect(
            Brush.radialGradient(listOf(Accent.copy(alpha = glow.value), Color.Transparent), center = Offset(w / 2f, h / 2f), radius = w * 0.85f),
            topLeft = Offset(-w * 0.12f, -h * 0.12f),
            size = Size(w * 1.24f, h * 1.24f),
            cornerRadius = CornerRadius(w * 0.4f, w * 0.4f)
        )
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF0B1B3F), Color(0xFF1E2A78), DevViolet), start = Offset(0f, 0f), end = Offset(w, h)),
            size = Size(w, h),
            cornerRadius = corner
        )
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Accent.copy(alpha = 0.6f)), start = Offset(0f, 0f), end = Offset(w, h)),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(width = w * 0.025f)
        )
        val s = Path().apply {
            moveTo(w * 0.69f, h * 0.30f)
            cubicTo(w * 0.62f, h * 0.19f, w * 0.38f, h * 0.17f, w * 0.34f, h * 0.34f)
            cubicTo(w * 0.30f, h * 0.51f, w * 0.70f, h * 0.49f, w * 0.66f, h * 0.67f)
            cubicTo(w * 0.62f, h * 0.84f, w * 0.37f, h * 0.82f, w * 0.29f, h * 0.70f)
        }
        val pm = PathMeasure()
        pm.setPath(s, false)
        val seg = Path()
        pm.getSegment(0f, pm.length * draw.value, seg, true)
        drawPath(seg, Color.White, style = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val bracket = Color(0xFF9DBEFF).copy(alpha = draw.value)
        val bw = w * 0.04f
        drawLine(bracket, Offset(w * 0.2f, h * 0.38f), Offset(w * 0.1f, h * 0.5f), bw, StrokeCap.Round)
        drawLine(bracket, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.2f, h * 0.62f), bw, StrokeCap.Round)
        drawLine(bracket, Offset(w * 0.8f, h * 0.38f), Offset(w * 0.9f, h * 0.5f), bw, StrokeCap.Round)
        drawLine(bracket, Offset(w * 0.9f, h * 0.5f), Offset(w * 0.8f, h * 0.62f), bw, StrokeCap.Round)
        drawCircle(DevGold.copy(alpha = draw.value), radius = w * 0.035f, center = Offset(w * 0.78f, h * 0.22f))
        val x = sweep.value * w
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(x - w * 0.25f, 0f),
                end = Offset(x + w * 0.1f, h)
            ),
            size = Size(w, h),
            cornerRadius = corner
        )
    }
}

@Composable
fun BrandLink(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "brandLink")
    val p = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "brandLinkP"
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(width = 56.dp, height = 24.dp)) {
        val y = size.height / 2f
        drawLine(color.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        for (i in 0 until 3) {
            val q = (p.value + i / 3f) % 1f
            val a = if (q < 0.5f) q * 2f else (1f - q) * 2f
            drawCircle(Accent.copy(alpha = a), radius = 3.dp.toPx(), center = Offset(size.width * q, y))
        }
    }
}

@Composable
fun BrandRow(markSize: Dp, modifier: Modifier = Modifier, devName: String, devRole: String, aiName: String, aiRole: String) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DevMark(markSize)
            Spacer(Modifier.height(10.dp))
            Text(devName, style = MaterialTheme.typography.titleMedium, color = cs.onBackground, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(devRole, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(8.dp))
        BrandLink()
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Box(Modifier.size(markSize), contentAlignment = Alignment.Center) { ClaudeMark(markSize) }
            Spacer(Modifier.height(10.dp))
            Text(aiName, style = MaterialTheme.typography.titleMedium, color = cs.onBackground, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(aiRole, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
