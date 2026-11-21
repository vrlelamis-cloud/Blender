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
    val Bg = Color(0xFF0B0D12)
    val Surface = Color(0xFF141821)
    val SurfaceHi = Color(0xFF1E2431)
    val Outline = Color(0xFF2B3345)
    val Accent = Color(0xFFFF7A1A)
    val AccentSoft = Color(0x33FF7A1A)
    val Blue = Color(0xFF5B8CFF)
    val Green = Color(0xFF3DDC97)
    val Pink = Color(0xFFFF5C93)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0xFF9AA3B5)
    val Danger = Color(0xFFFF5470)
}

private val scheme = darkColorScheme(
    primary = BColors.Accent, onPrimary = Color(0xFF1A0E00),
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
