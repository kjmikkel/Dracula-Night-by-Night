package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Restrained gothic baseline: oxblood on parchment, bone on near-black. Plan 2 adds fonts and ornaments.
private val Light = lightColorScheme(
    primary = Color(0xFF7A1F24),
    onPrimary = Color(0xFFFBF6EC),
    secondary = Color(0xFF75655A),
    background = Color(0xFFF4ECDC),
    onBackground = Color(0xFF2A1F1A),
    surface = Color(0xFFFBF6EC),
    onSurface = Color(0xFF2A1F1A),
    surfaceVariant = Color(0xFFEADFCB),
    onSurfaceVariant = Color(0xFF5C4D43),
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = Color(0xFFF8F0E2),
    surfaceContainer = Color(0xFFF1E7D6),
    surfaceContainerHigh = Color(0xFFEBE0CD),
    surfaceContainerHighest = Color(0xFFE5D9C4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD06A6A),
    onPrimary = Color(0xFF1D1718),
    secondary = Color(0xFFA39282),
    background = Color(0xFF141011),
    onBackground = Color(0xFFECE2D4),
    surface = Color(0xFF1D1718),
    onSurface = Color(0xFFECE2D4),
    surfaceVariant = Color(0xFF2A2223),
    onSurfaceVariant = Color(0xFFBFAF9F),
    surfaceContainerLowest = Color(0xFF0F0C0D),
    surfaceContainerLow = Color(0xFF1A1516),
    surfaceContainer = Color(0xFF211A1B),
    surfaceContainerHigh = Color(0xFF2B2324),
    surfaceContainerHighest = Color(0xFF362C2D),
)

private val Serif = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    )
}

val TelegramStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 1.sp)

@Composable
fun DraculaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Serif, content = content)
}
