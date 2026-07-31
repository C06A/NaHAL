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
    accent    = Color(0xFF6DD497),
    accentSoft = Color(0x226DD497),
    ok        = Color(0xFF6DD497),
    warn      = Color(0xFFE2B057),
    err       = Color(0xFFE26C6C),
    redir     = Color(0xFF6CB6E2),
    info      = Color(0xFF9AA0AD),
)

object JsonColors {
    val key    = Color(0xFF6DD497)
    val string = Color(0xFFB8D99A)
    val number = Color(0xFFE2B057)
    val bool   = Color(0xFFC79CE8)
    val null_  = Color(0xFF8B91A0)
}

object NaHalDimens {
    val railWidth: Dp     = 300.dp
    val topBarHeight: Dp  = 40.dp
    val borderWidth: Dp   = 1.dp
    val cornerRadius: Dp  = 5.dp
    val railCorner: Dp    = 3.dp

    // ── Resizable panes ──────────────────────────────────────────────────────
    /**
     * Grab width of a pane splitter — the hairline it draws stays [borderWidth], this is only the
     * pointer hit area around it. Widths below are the floors a drag can shrink a pane to; the
     * ceilings come from the container, so a pane can never be dragged past its neighbour's floor.
     */
    val splitterHit: Dp      = 7.dp
    val minRailWidth: Dp     = 180.dp
    val minCenterWidth: Dp   = 320.dp
    val minCanvasWidth: Dp   = 280.dp
    val minDrawerWidth: Dp   = 260.dp
    val minRailSection: Dp   = 90.dp
    /** Default height of the rail's Traversal section; the Request Log takes the rest. */
    val traversalHeight: Dp  = 340.dp

    // ── Graph layout (design variant C) ──────────────────────────────────────
    val drawerWidth: Dp      = 420.dp
    /**
     * The graph canvas lays out in character cells, like `grapher.sh`'s SVG renderer: one cell is
     * one monospace advance of [graphTextSize] wide and one text row ([graphRowH]) high. The canvas
     * measures the advance from the font itself; [graphCharW] (~0.6em) is only the floor it can't
     * drop below when the platform reports nothing.
     */
    val graphCharW: Dp       = 6.6.dp
    val graphRowH: Dp        = 14.dp
    val graphLineHeight      = 14.sp
    val graphTextSize        = 11.sp
    val graphPad: Dp         = 24.dp
    val graphNodeCorner: Dp  = 4.dp
    val graphGridStep: Dp    = 20.dp
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
