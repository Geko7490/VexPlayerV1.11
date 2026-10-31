package com.vexmusic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VexPurple = Color(0xFFA855F7)
val VexPurpleDark = Color(0xFF7E22CE)
val VexPurpleSoft = Color(0xFFC78FFB)
val VexBackground = Color(0xFF0D0C10)
val VexSurface = Color(0xFF141319)
val VexCard = Color(0xFF1A1920)
val VexTextPrimary = Color(0xFFFFFFFF)
val VexTextSecondary = Color(0xFFA1A1AA)
val VexDivider = Color(0xFF2A2933)
val VexError = Color(0xFFF87171)
val VexSuccess = Color(0xFF4ADE80)

private val VexColorScheme = darkColorScheme(
    primary = VexPurple,
    onPrimary = Color.White,
    primaryContainer = VexPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = VexPurpleSoft,
    onSecondary = Color.Black,
    background = VexBackground,
    onBackground = VexTextPrimary,
    surface = VexSurface,
    onSurface = VexTextPrimary,
    surfaceVariant = VexCard,
    onSurfaceVariant = VexTextSecondary,
    outline = VexDivider,
    error = VexError,
    onError = Color.Black
)

val VexShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(7.dp),
    large = RoundedCornerShape(9.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

private val VexTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, fontFamily = FontFamily.Monospace),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, fontFamily = FontFamily.Monospace),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = VexTextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, fontFamily = FontFamily.Monospace),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp, fontFamily = FontFamily.Monospace)
)

val VexGradient = Brush.verticalGradient(
    listOf(Color(0xFF141119), VexBackground)
)

val VexAccentGradient = Brush.horizontalGradient(
    listOf(VexPurple, Color(0xFF6D28D9))
)

@Composable
fun VexMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VexColorScheme,
        typography = VexTypography,
        shapes = VexShapes,
        content = content
    )
}
