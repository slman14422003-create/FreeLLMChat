package com.freellm.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color.White,
    secondary = Color(0xFFC96442),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE8E8E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    error = Color(0xFFD93025)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF19C37D),
    onPrimary = Color(0xFF003824),
    secondary = Color(0xFFE08A6B),
    onSecondary = Color(0xFF3A1A0E),
    background = Color(0xFF212121),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF171717),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF2F2F2F),
    error = Color(0xFFFF6B6B)
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun FreeLlmTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
