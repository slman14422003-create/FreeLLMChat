package com.freellm.chat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Ok = Color(0xFF34C759)

@Composable
private fun rememberLoop(ms: Int): State<Float> {
    val t = rememberInfiniteTransition(label = "loop")
    return t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart),
        label = "loopP"
    )
}

private fun easeInOut(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

@Composable
fun RoutingArt(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(5200)
    val node = cs.surfaceContainerHighest
    val line = cs.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val src = Offset(w * 0.17f, h * 0.5f)
        val n = 4
        val ph = loop.value * n
        val active = ph.toInt().coerceAtMost(n - 1)
        val local = ph - active
        val r = min(w * 0.065f, h * 0.085f)
        for (i in 0 until n) {
            val dst = Offset(w * 0.84f, h * (0.13f + 0.74f * i / (n - 1)))
            val on = i == active
            drawLine(
                if (on) Accent.copy(alpha = 0.7f) else line.copy(alpha = 0.35f),
                src, dst,
                strokeWidth = (if (on) 3f else 1.5f).dp.toPx(),
                cap = StrokeCap.Round
            )
            val arrived = on && local > 0.7f
            if (on) {
                val k = easeInOut(local / 0.7f)
                val p = lerpOffset(src, dst, k)
                if (local <= 0.7f) {
                    drawCircle(Accent.copy(alpha = 0.3f), radius = 11.dp.toPx(), center = p)
                    drawCircle(Accent, radius = 5.dp.toPx(), center = p)
                } else {
                    val g = (local - 0.7f) / 0.3f
                    drawCircle(Accent.copy(alpha = 0.4f * (1f - g)), radius = r * (1f + 1.3f * g), center = dst)
                }
            }
            drawCircle(node, radius = r, center = dst)
            drawCircle(
                if (arrived) Accent else line.copy(alpha = 0.7f),
                radius = r, center = dst,
                style = Stroke(2.dp.toPx())
            )
            drawCircle(if (arrived) Accent else line.copy(alpha = 0.5f), radius = r * 0.32f, center = dst)
        }
        if (local < 0.35f) {
            val g = local / 0.35f
            drawCircle(Accent.copy(alpha = 0.35f * (1f - g)), radius = 26.dp.toPx() * (1f + g), center = src, style = Stroke(2.dp.toPx()))
        }
        val bw = 46.dp.toPx()
        val bh = 34.dp.toPx()
        drawRoundRect(
            Accent,
            topLeft = Offset(src.x - bw / 2f, src.y - bh / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        for (d in 0 until 3) {
            val bob = sin((loop.value * 6f * PI + d * 0.9f).toFloat())
            drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(src.x + (d - 1) * 9.dp.toPx(), src.y - 2.dp.toPx() * bob))
        }
    }
}

@Composable
fun FailoverArt(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(6000)
    val node = cs.surfaceContainerHighest
    val line = cs.outline
    val bad = cs.error
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = min(w * 0.12f, h * 0.2f)
        val a = Offset(w * 0.17f, h * 0.62f)
        val b = Offset(w * 0.5f, h * 0.62f)
        val c = Offset(w * 0.83f, h * 0.62f)
        val t = loop.value
        val fade = if (t > 0.9f) (t - 0.9f) / 0.1f else 0f
        val keep = 1f - fade
        val drain = (t / 0.3f).coerceIn(0f, 1f)
        val hop = easeInOut((t - 0.32f) / 0.2f)
        val fill = ((t - 0.55f) / 0.2f).coerceIn(0f, 1f)
        val stroke = 3.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
        drawLine(line.copy(alpha = 0.4f), b, c, strokeWidth = 1.5f.dp.toPx(), pathEffect = dash)
        val up = r + 14.dp.toPx()
        val restA = Offset(a.x, a.y - up)
        val restB = Offset(b.x, b.y - up)
        val ctrl = Offset((restA.x + restB.x) / 2f, restA.y - h * 0.3f)
        val arc = Path().apply {
            moveTo(restA.x, restA.y)
            quadraticBezierTo(ctrl.x, ctrl.y, restB.x, restB.y)
        }
        drawPath(arc, Accent.copy(alpha = 0.35f), style = Stroke(1.5f.dp.toPx(), pathEffect = dash))
        listOf(a, b, c).forEach { drawCircle(node, radius = r, center = it) }
        listOf(a, b, c).forEach {
            drawCircle(line.copy(alpha = 0.6f), radius = r, center = it, style = Stroke(stroke))
        }
        drawArc(
            bad.copy(alpha = keep), -90f, 360f * drain, false,
            topLeft = Offset(a.x - r, a.y - r), size = Size(r * 2f, r * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        if (drain >= 1f) {
            val k = r * 0.38f
            drawLine(bad.copy(alpha = keep), Offset(a.x - k, a.y - k), Offset(a.x + k, a.y + k), stroke, StrokeCap.Round)
            drawLine(bad.copy(alpha = keep), Offset(a.x + k, a.y - k), Offset(a.x - k, a.y + k), stroke, StrokeCap.Round)
        }
        drawArc(
            Ok.copy(alpha = keep), -90f, 360f * fill, false,
            topLeft = Offset(b.x - r, b.y - r), size = Size(r * 2f, r * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        if (fill >= 1f) {
            val ck = Path().apply {
                moveTo(b.x - r * 0.4f, b.y)
                lineTo(b.x - r * 0.08f, b.y + r * 0.32f)
                lineTo(b.x + r * 0.42f, b.y - r * 0.3f)
            }
            drawPath(ck, Ok.copy(alpha = keep), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(line.copy(alpha = 0.5f), radius = r * 0.25f, center = c)
        val dot = when {
            t < 0.32f -> restA
            t < 0.52f -> Offset(
                (1f - hop) * (1f - hop) * restA.x + 2f * (1f - hop) * hop * ctrl.x + hop * hop * restB.x,
                (1f - hop) * (1f - hop) * restA.y + 2f * (1f - hop) * hop * ctrl.y + hop * hop * restB.y
            )
            else -> restB
        }
        drawCircle(Accent.copy(alpha = 0.3f * keep), radius = 12.dp.toPx(), center = dot)
        drawCircle(Accent.copy(alpha = keep), radius = 6.dp.toPx(), center = dot)
    }
}

@Composable
fun KeysArt(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(5600)
    val node = cs.surfaceContainerHighest
    val line = cs.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val t = loop.value
        val fade = if (t > 0.92f) (t - 0.92f) / 0.08f else 0f
        val keep = 1f - fade
        val cardW = w * 0.5f
        val cardH = h * 0.5f
        val cardTL = Offset(w * 0.42f, h * 0.25f)
        val ins = easeInOut(t / 0.45f)
        val ok = ((t - 0.5f) / 0.15f).coerceIn(0f, 1f)
        val border = if (ok > 0f) androidx.compose.ui.graphics.lerp(line.copy(alpha = 0.7f), Ok, ok) else line.copy(alpha = 0.7f)
        drawRoundRect(node, topLeft = cardTL, size = Size(cardW, cardH), cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()))
        drawRoundRect(
            border.copy(alpha = border.alpha * keep), topLeft = cardTL, size = Size(cardW, cardH),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()), style = Stroke(2.5f.dp.toPx())
        )
        val dots = (ok * 6f).toInt()
        for (i in 0 until 6) {
            val on = i < dots
            drawCircle(
                if (on) Accent.copy(alpha = keep) else line.copy(alpha = 0.3f),
                radius = 4.dp.toPx(),
                center = Offset(cardTL.x + cardW * 0.14f + i * cardW * 0.1f, cardTL.y + cardH * 0.62f)
            )
        }
        drawRoundRect(
            line.copy(alpha = 0.35f), topLeft = Offset(cardTL.x + cardW * 0.14f, cardTL.y + cardH * 0.2f),
            size = Size(cardW * 0.4f, 7.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        val kr = 15.dp.toPx()
        val len = w * 0.3f
        val startX = w * 0.08f + kr
        val endX = cardTL.x - len * 0.15f + kr
        val kx = (startX + (endX - startX) * ins)
        val ky = cardTL.y + cardH * 0.5f
        val kc = DevGold.copy(alpha = keep)
        val sw = 5.dp.toPx()
        drawCircle(kc, radius = kr, center = Offset(kx, ky), style = Stroke(sw))
        drawLine(kc, Offset(kx + kr, ky), Offset(kx + kr + len, ky), sw, StrokeCap.Round)
        drawLine(kc, Offset(kx + kr + len * 0.7f, ky), Offset(kx + kr + len * 0.7f, ky + 11.dp.toPx()), sw, StrokeCap.Round)
        drawLine(kc, Offset(kx + kr + len * 0.9f, ky), Offset(kx + kr + len * 0.9f, ky + 8.dp.toPx()), sw, StrokeCap.Round)
        if (ok > 0f) {
            val c = Offset(cardTL.x + cardW - 22.dp.toPx(), cardTL.y + 22.dp.toPx())
            val rr = 11.dp.toPx() * ok
            drawCircle(Ok.copy(alpha = keep), radius = rr, center = c)
            val ck = Path().apply {
                moveTo(c.x - rr * 0.45f, c.y)
                lineTo(c.x - rr * 0.1f, c.y + rr * 0.35f)
                lineTo(c.x + rr * 0.48f, c.y - rr * 0.35f)
            }
            drawPath(ck, Color.White.copy(alpha = keep), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun PrivacyArt(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(6000)
    val node = cs.surfaceContainerHighest
    val line = cs.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val pw = min(w * 0.26f, h * 0.4f)
        val ph = pw * 1.9f
        for (k in 0 until 3) {
            val q = (loop.value * 2f + k / 3f) % 1f
            drawCircle(Accent.copy(alpha = (1f - q) * 0.4f), radius = pw * 0.75f + q * pw * 1.7f, center = c, style = Stroke(2.dp.toPx()))
        }
        val tl = Offset(c.x - pw / 2f, c.y - ph / 2f)
        drawRoundRect(node, topLeft = tl, size = Size(pw, ph), cornerRadius = CornerRadius(pw * 0.2f, pw * 0.2f))
        drawRoundRect(line, topLeft = tl, size = Size(pw, ph), cornerRadius = CornerRadius(pw * 0.2f, pw * 0.2f), style = Stroke(3.dp.toPx()))
        drawRoundRect(line.copy(alpha = 0.6f), topLeft = Offset(c.x - pw * 0.14f, tl.y + ph * 0.035f), size = Size(pw * 0.28f, 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        val lw = pw * 0.56f
        val lh = lw * 0.78f
        val bodyTop = c.y - lh * 0.1f
        val pulse = 1f + 0.04f * sin((loop.value * 2f * PI * 3f).toFloat())
        drawArc(
            Accent, 180f, 180f, false,
            topLeft = Offset(c.x - lw * 0.3f, bodyTop - lw * 0.5f),
            size = Size(lw * 0.6f, lw * 0.6f * pulse),
            style = Stroke(lw * 0.11f, cap = StrokeCap.Round)
        )
        drawRoundRect(Accent, topLeft = Offset(c.x - lw / 2f, bodyTop), size = Size(lw, lh), cornerRadius = CornerRadius(lw * 0.16f, lw * 0.16f))
        drawCircle(Color.White, radius = lw * 0.09f, center = Offset(c.x, bodyTop + lh * 0.4f))
        drawLine(Color.White, Offset(c.x, bodyTop + lh * 0.45f), Offset(c.x, bodyTop + lh * 0.7f), lw * 0.07f, StrokeCap.Round)
        val colors = listOf(ClaudeOrange, DevGold, DevViolet, Accent)
        for (i in 0 until 4) {
            val ang = loop.value * 2f * PI + i * PI / 2.0
            val p = Offset(c.x + (cos(ang) * pw * 1.25f).toFloat(), c.y + (sin(ang) * ph * 0.48f).toFloat())
            val s = 7.dp.toPx()
            drawRoundRect(colors[i].copy(alpha = 0.9f), topLeft = Offset(p.x - s / 2f, p.y - s / 2f), size = Size(s, s), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

@Composable
private fun NetworkArt(modifier: Modifier, pts: List<Offset>, links: List<Pair<Int, Int>>, hub: Int, ms: Int) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(ms)
    val node = cs.surfaceContainerHighest
    val line = cs.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = pts.map { Offset(it.x * w, it.y * h) }
        links.forEachIndexed { j, (a, b) ->
            drawLine(line.copy(alpha = 0.3f), p[a], p[b], strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
            val q = (loop.value * 2f + j * 0.173f) % 1f
            val al = 1f - abs(q - 0.5f) * 2f
            drawCircle(Accent.copy(alpha = al), radius = 3.dp.toPx(), center = lerpOffset(p[a], p[b], q))
        }
        p.forEachIndexed { i, o ->
            val beat = 0.5f + 0.5f * sin((loop.value * 4f * PI + i * 1.3f).toFloat())
            val base = if (i == hub) 13.dp.toPx() else 8.dp.toPx()
            drawCircle(Accent.copy(alpha = 0.18f * beat), radius = base * (1.4f + 0.6f * beat), center = o)
            drawCircle(if (i == hub) Accent else node, radius = base, center = o)
            drawCircle(if (i == hub) Color.White else Accent.copy(alpha = 0.5f + 0.5f * beat), radius = base * 0.4f, center = o)
        }
    }
}

@Composable
fun ProvidersArt(modifier: Modifier = Modifier) {
    val pts = listOf(
        Offset(0.5f, 0.5f), Offset(0.14f, 0.28f), Offset(0.3f, 0.82f), Offset(0.72f, 0.84f),
        Offset(0.88f, 0.3f), Offset(0.5f, 0.12f)
    )
    val links = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 5, 4 to 5)
    NetworkArt(modifier, pts, links, 0, 4200)
}

@Composable
fun BrainArt(modifier: Modifier = Modifier) {
    val pts = listOf(
        Offset(0.2f, 0.5f), Offset(0.36f, 0.2f), Offset(0.36f, 0.8f), Offset(0.52f, 0.5f),
        Offset(0.68f, 0.22f), Offset(0.68f, 0.78f), Offset(0.84f, 0.5f)
    )
    val links = listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3, 1 to 4, 3 to 4, 3 to 5, 2 to 5, 4 to 6, 5 to 6, 1 to 2, 4 to 5)
    NetworkArt(modifier, pts, links, 3, 5000)
}

@Composable
fun UsageArt(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val loop = rememberLoop(4000)
    val line = cs.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val n = 6
        val gap = w * 0.04f
        val bw = (w * 0.8f - gap * (n - 1)) / n
        val x0 = w * 0.1f
        val base = h * 0.9f
        drawLine(line.copy(alpha = 0.5f), Offset(x0 - 6.dp.toPx(), base), Offset(w * 0.9f + 6.dp.toPx(), base), 2.dp.toPx(), StrokeCap.Round)
        for (i in 0 until n) {
            val v = 0.45f + 0.45f * sin((loop.value * 2f * PI + i * 0.95f).toFloat())
            val bh = (base - h * 0.08f) * v
            val tl = Offset(x0 + i * (bw + gap), base - bh)
            drawRoundRect(
                Brush.verticalGradient(listOf(if (v > 0.8f) cs.error else Accent, Accent.copy(alpha = 0.35f)), startY = tl.y, endY = base),
                topLeft = tl, size = Size(bw, bh), cornerRadius = CornerRadius(bw * 0.3f, bw * 0.3f)
            )
        }
    }
}

private fun ecg(u: Float): Float = when {
    u < 0.18f -> 0f
    u < 0.24f -> 0.18f * sin(((u - 0.18f) / 0.06f * PI).toFloat())
    u < 0.3f -> 0f
    u < 0.33f -> -0.2f * ((u - 0.3f) / 0.03f)
    u < 0.38f -> -0.2f + 1.2f * ((u - 0.33f) / 0.05f)
    u < 0.44f -> 1f - 1.35f * ((u - 0.38f) / 0.06f)
    u < 0.48f -> -0.35f + 0.35f * ((u - 0.44f) / 0.04f)
    u < 0.6f -> 0f
    u < 0.7f -> 0.28f * sin(((u - 0.6f) / 0.1f * PI).toFloat())
    else -> 0f
}

@Composable
fun StatusArt(modifier: Modifier = Modifier) {
    val loop = rememberLoop(3200)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path()
        val steps = 160
        for (i in 0..steps) {
            val x = i / steps.toFloat()
            val u = (x * 2f - loop.value * 1f + 2f) % 1f
            val y = h * 0.55f - ecg(u) * h * 0.4f
            if (i == 0) path.moveTo(x * w, y) else path.lineTo(x * w, y)
        }
        drawPath(
            path,
            Brush.horizontalGradient(listOf(Color.Transparent, Ok, Ok, Color.Transparent)),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val pulse = (loop.value * 2f) % 1f
        drawCircle(Ok.copy(alpha = 0.35f * (1f - pulse)), radius = 6.dp.toPx() + 16.dp.toPx() * pulse, center = Offset(w * 0.5f, h * 0.55f))
        drawCircle(Ok, radius = 5.dp.toPx(), center = Offset(w * 0.5f, h * 0.55f))
    }
}

@Composable
fun ChatHeroArt(dim: Dp, modifier: Modifier = Modifier) {
    val loop = rememberLoop(9000)
    val colors = listOf(Accent, ClaudeOrange, DevViolet)
    Box(modifier.size(dim * 2.4f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = dim.toPx() * 0.55f
            for (k in 0 until 3) {
                val q = (loop.value * 3f + k / 3f) % 1f
                drawCircle(Accent.copy(alpha = (1f - q) * 0.28f), radius = base + q * dim.toPx() * 0.9f, center = c, style = Stroke(2.dp.toPx()))
            }
            for (i in 0 until 3) {
                val ang = loop.value * 2f * PI + i * 2f * PI / 3f
                val rad = dim.toPx() * (0.95f + 0.08f * sin((loop.value * 6f * PI + i).toFloat()))
                val o = Offset(c.x + (cos(ang) * rad).toFloat(), c.y + (sin(ang) * rad * 0.8f).toFloat())
                val bw = 16.dp.toPx()
                val bh = 11.dp.toPx()
                drawRoundRect(
                    colors[i].copy(alpha = 0.85f), topLeft = Offset(o.x - bw / 2f, o.y - bh / 2f),
                    size = Size(bw, bh), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
            }
        }
        StarBurst(size = dim)
    }
}
