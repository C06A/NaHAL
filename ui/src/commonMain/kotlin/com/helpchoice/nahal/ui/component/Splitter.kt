package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.NaHalDimens

/**
 * Draggable pane divider. Draws the same 1dp hairline [NaHalDivider] does, but inside a wider
 * invisible hit area ([NaHalDimens.splitterHit]) so it can be grabbed, and highlights in the accent
 * colour while hovered or dragged.
 *
 * The splitter is stateless: [onDelta] receives the drag distance in dp (positive = right / down)
 * and the caller clamps and stores the new pane size. [onReset] fires on double-click, for
 * restoring the default size.
 */
@Composable
fun VerticalSplitter(
    onDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
) {
    Splitter(
        orientation = Orientation.Horizontal,
        onDelta = onDelta,
        onReset = onReset,
        modifier = modifier.fillMaxHeight().width(NaHalDimens.splitterHit),
        lineModifier = Modifier.fillMaxHeight().width(NaHalDimens.borderWidth),
    )
}

/** [VerticalSplitter] laid on its side: drag up/down to resize the pane above it. */
@Composable
fun HorizontalSplitter(
    onDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
) {
    Splitter(
        orientation = Orientation.Vertical,
        onDelta = onDelta,
        onReset = onReset,
        modifier = modifier.fillMaxWidth().height(NaHalDimens.splitterHit),
        lineModifier = Modifier.fillMaxWidth().height(NaHalDimens.borderWidth),
    )
}

@Composable
private fun Splitter(
    orientation: Orientation,
    onDelta: (Dp) -> Unit,
    onReset: (() -> Unit)?,
    modifier: Modifier,
    lineModifier: Modifier,
) {
    val c = LocalNaHalColors.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { px -> onDelta(with(density) { px.toDp() }) },
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false },
            )
            .then(
                if (onReset == null) Modifier
                else Modifier.pointerInput(onReset) {
                    detectTapGestures(onDoubleTap = { onReset() })
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = lineModifier.background(if (dragging || hovered) c.accent else c.border))
    }
}
