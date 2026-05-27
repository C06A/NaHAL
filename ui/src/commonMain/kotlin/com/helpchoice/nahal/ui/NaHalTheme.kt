package com.helpchoice.nahal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NaHalColors(
    val bg: Color,
    val bg2: Color,
    val bg3: Color,
    val bgHover: Color,
    val border: Color,
    val border2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentSoft: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val redir: Color,
    val info: Color,
)

val naHalDarkColors = NaHalColors(
    bg        = Color(0xFF0E1014),
    bg2       = Color(0xFF14171C),
    bg3       = Color(0xFF1A1E25),
    bgHover   = Color(0xFF1F2430),
    border    = Color(0xFF232831),
    border2   = Color(0xFF2C323C),
    text      = Color(0xFFD6DAE3),
    text2     = Color(0xFF9AA0AD),
    text3     = Color(0xFF636978),
    accent    = Color(0xFF8B7CFF),
    accentSoft = Color(0x228B7CFF),
    ok        = Color(0xFF6DD497),
    warn      = Color(0xFFE2B057),
    err       = Color(0xFFE26C6C),
    redir     = Color(0xFF6CB6E2),
    info      = Color(0xFF9AA0AD),
)

object JsonColors {
    val key    = Color(0xFF8B7CFF)
    val string = Color(0xFF8FC8A8)
    val number = Color(0xFFD99A6C)
    val bool   = Color(0xFFC79CE8)
    val null_  = Color(0xFF8B91A0)
}

object NaHalDimens {
    val railWidth: Dp     = 300.dp
    val topBarHeight: Dp  = 40.dp
    val borderWidth: Dp   = 1.dp
    val cornerRadius: Dp  = 5.dp
    val railCorner: Dp    = 3.dp
}

val LocalNaHalColors = staticCompositionLocalOf { naHalDarkColors }
val LocalCurrentUrl  = compositionLocalOf { "" }
val NaHalMonoFont: FontFamily = FontFamily.Monospace
val NaHalSansFont: FontFamily = FontFamily.Default

@Composable
fun NaHalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNaHalColors provides naHalDarkColors) {
        content()
    }
}
