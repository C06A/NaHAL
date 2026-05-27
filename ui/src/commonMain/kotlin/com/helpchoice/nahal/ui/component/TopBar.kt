package com.helpchoice.nahal.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.ui.NaHalDimens
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.state.NavigatorState

@Composable
fun NaHalTopBar(
    state: NavigatorState,
    onNavigate: (String) -> Unit,
) {
    val c = LocalNaHalColors.current
    var addressText by remember { mutableStateOf("") }

    // Sync address bar with current URL when it changes
    val currentUrl = state.current?.url ?: ""
    LaunchedEffect(currentUrl) {
        if (addressText.isBlank()) addressText = currentUrl
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NaHalDimens.topBarHeight)
            .background(c.bg2)
            .border(width = 1.dp, color = c.border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Mini graph icon (circles)
            Box(modifier = Modifier.size(14.dp)) {
                Box(modifier = Modifier.size(4.4.dp).offset(x = 4.8.dp, y = 4.8.dp).clip(CircleShape).background(c.accent))
                Box(modifier = Modifier.size(2.8.dp).offset(x = 0.dp, y = 0.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.6f)))
                Box(modifier = Modifier.size(2.8.dp).offset(x = 11.2.dp, y = 0.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.6f)))
                Box(modifier = Modifier.size(2.8.dp).offset(x = 0.dp, y = 11.2.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.6f)))
                Box(modifier = Modifier.size(2.8.dp).offset(x = 11.2.dp, y = 11.2.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.6f)))
            }
            Text(
                text = "NaHAL",
                color = c.text,
                fontSize = 13.sp,
                fontFamily = NaHalMonoFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
        }

        // Back / forward
        NavIconButton(
            label = "←",
            enabled = state.canGoBack,
            onClick = state::goBack,
        )
        NavIconButton(
            label = "→",
            enabled = state.canGoForward,
            onClick = state::goForward,
        )

        // Address bar
        Row(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(c.bg3)
                .border(1.dp, c.border2, RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.current != null) {
                MethodBadge(state.current!!.method)
            }

            // Editable URL
            androidx.compose.foundation.text.BasicTextField(
                value = addressText,
                onValueChange = { addressText = it },
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = c.text,
                    fontFamily = NaHalMonoFont,
                    fontSize = 12.sp,
                ),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            val url = addressText.trim()
                            if (url.isNotBlank()) onNavigate(url)
                            true
                        } else false
                    },
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (addressText.isEmpty()) {
                            Text(
                                "Enter URL and press Enter…",
                                color = c.text3,
                                fontSize = 12.sp,
                                fontFamily = NaHalMonoFont,
                            )
                        }
                        inner()
                    }
                },
            )

            if (state.loading) {
                LoadingSpinner(color = c.accent)
            }
        }

        // Go button
        val canGo = addressText.isNotBlank() && !state.loading
        NavIconButton(
            label = "Go",
            enabled = canGo,
            onClick = { if (canGo) onNavigate(addressText.trim()) },
        )

        // Status pill + elapsed
        val cur = state.current
        if (cur != null) {
            StatusPill(code = cur.response.status, statusText = cur.response.statusText)
            Text(
                text = "${cur.elapsedMs}ms",
                color = c.text3,
                fontSize = 11.sp,
                fontFamily = NaHalMonoFont,
            )
        }
    }
}

@Composable
private fun NavIconButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.bg3)
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.text2 else c.text3.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = NaHalMonoFont,
        )
    }
}

@Composable
private fun LoadingSpinner(color: Color) {
    val c = LocalNaHalColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
        ),
    )
    Canvas(modifier = Modifier.size(10.dp).rotate(angle)) {
        val sw = 1.5.dp.toPx()
        drawArc(
            color = c.border2,
            startAngle = 0f, sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}
