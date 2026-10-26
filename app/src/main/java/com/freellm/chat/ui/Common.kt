package com.freellm.chat.ui

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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Claude-style sub-screen header: round back button, centered serif title. */
@Composable
fun AppTopBar(title: String, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
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

/** Radial burst mark (Claude's sparkle, drawn in ChatGPT green). */
@Composable
fun StarBurst(size: Dp, modifier: Modifier = Modifier, color: Color = GptGreen, spinning: Boolean = false) {
    val angle = if (spinning) {
        val t = rememberInfiniteTransition(label = "burst")
        t.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "burstAngle"
        ).value
    } else 0f
    Canvas(modifier.size(size).rotate(angle)) {
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
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
        Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
