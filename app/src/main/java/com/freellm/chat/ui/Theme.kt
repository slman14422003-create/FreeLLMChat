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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ChatGPT palette: #212121 canvas, #171717 sidebar, #2F2F2F raised surfaces, #10A37F brand green. */
val GptGreen = Color(0xFF10A37F)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D0D0D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECECEC),
    onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = Color(0xFFC2410C),
    onSecondary = Color.White,
    tertiary = GptGreen,
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF5D5D5D),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE5E5E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFF4F4F4),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    error = Color(0xFFE02E2A),
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFECECEC),
    secondary = Color(0xFFE5A04B),
    onSecondary = Color(0xFF2B1A00),
    tertiary = GptGreen,
    onTertiary = Color.White,
    background = Color(0xFF212121),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF4D4D4D),
    outlineVariant = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF171717),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF2A2A2A),
    surfaceContainerHigh = Color(0xFF2F2F2F),
    surfaceContainerHighest = Color(0xFF424242),
    error = Color(0xFFF93A37),
    scrim = Color(0xFF000000)
)

/** Claude-style typography: serif display/headings, clean sans for body. */
private val Serif = FontFamily.Serif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
