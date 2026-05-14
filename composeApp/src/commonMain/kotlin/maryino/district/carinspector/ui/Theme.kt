package com.carinspector.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// PALETTE
// Dark: near-black navy base, teal accent, semantic colours
// ─────────────────────────────────────────────────────────────────────────────

object Clr {
    // backgrounds
    val Bg          = Color(0xFF090C13)
    val Surface     = Color(0xFF10141F)
    val SurfaceHigh = Color(0xFF161C2A)
    val Card        = Color(0xFF13182300)   // transparent — used with border
    val CardSolid   = Color(0xFF131823)
    val Border      = Color(0xFF1E2638)
    val BorderLight = Color(0xFF2A3448)

    // accent
    val Teal        = Color(0xFF00C9A7)
    val TealDim     = Color(0x1A00C9A7)
    val TealGlow    = Color(0x0800C9A7)

    // semantic
    val Green       = Color(0xFF34D399)
    val GreenDim    = Color(0x1A34D399)
    val Amber       = Color(0xFFFBBF24)
    val AmberDim    = Color(0x1AFBBF24)
    val Red         = Color(0xFFF87171)
    val RedDim      = Color(0x1AF87171)
    val Blue        = Color(0xFF60A5FA)
    val BlueDim     = Color(0x1A60A5FA)

    // text
    val T1          = Color(0xFFEDF2FA)
    val T2          = Color(0xFF8896AE)
    val T3          = Color(0xFF4A5568)
    val TOnAccent   = Color(0xFF001A14)

    // misc
    val Divider     = Color(0xFF1A2030)
}

// ─────────────────────────────────────────────────────────────────────────────
// TYPOGRAPHY  (system sans — swap for custom font in production)
// ─────────────────────────────────────────────────────────────────────────────

val AppTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.W200, fontSize = 52.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.W300, fontSize = 38.sp, letterSpacing = (-1.2).sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.W300, fontSize = 30.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.W500, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W500, fontSize = 17.sp, letterSpacing = 0.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.W600, fontSize = 15.sp, letterSpacing = 0.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = 0.1.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.W400, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 10.sp, letterSpacing = 0.8.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 9.sp,  letterSpacing = 1.sp),
)

// ─────────────────────────────────────────────────────────────────────────────
// MATERIAL THEME
// ─────────────────────────────────────────────────────────────────────────────

private val scheme = darkColorScheme(
    primary          = Clr.Teal,
    onPrimary        = Clr.TOnAccent,
    background       = Clr.Bg,
    surface          = Clr.Surface,
    surfaceVariant   = Clr.SurfaceHigh,
    onBackground     = Clr.T1,
    onSurface        = Clr.T1,
    onSurfaceVariant = Clr.T2,
    outline          = Clr.Border,
    error            = Clr.Red,
    tertiary         = Clr.Amber,
)

@Composable
fun CarInspectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
