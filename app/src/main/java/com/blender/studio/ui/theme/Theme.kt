package com.blender.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object BColors {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0D)
    val SurfaceHi = Color(0xFF1A1A1A)
    val Outline = Color(0xFF3A3A3A)
    val Accent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0x33FFFFFF)
    val Blue = Color(0xFFFFFFFF)
    val Green = Color(0xFFD9D9D9)
    val Pink = Color(0xFFBDBDBD)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0xFFA6A6A6)
    val Danger = Color(0xFFFFFFFF)
}

private val scheme = darkColorScheme(
    primary = BColors.Accent, onPrimary = Color(0xFF000000),
    secondary = BColors.Blue, background = BColors.Bg, onBackground = BColors.Text,
    surface = BColors.Surface, onSurface = BColors.Text,
    surfaceVariant = BColors.SurfaceHi, onSurfaceVariant = BColors.TextDim,
    outline = BColors.Outline, error = BColors.Danger,
)

private val typo = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun BlenderTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
