package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.NaHalDimens
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.model.HistoryNode
import com.helpchoice.nahal.ui.model.buildCurlCommand
import com.helpchoice.nahal.ui.model.shortenUrl

// ── Geometry, in character cells (a port of grapher.sh's `_lr_compute_layout`) ────────────────

/** Widest a box may get, in characters — longer lines are truncated with an ellipsis. */
private const val MAX_BOX_COLS = 72

/** Narrowest gutter between two columns of boxes; a wide edge label widens its own gutter. */
private const val MIN_EDGE_GAP = 8

/** Blank rows between vertically stacked boxes. */
private const val ROW_GAP = 2

/**
 * One node as the layout sees it: a titled box of monospace [body] lines, hanging off [parentId]
 * by an edge captioned [edgeLabel].
 */
data class GraphNodeSpec(
    val id: String,
    val parentId: String?,
    /** Box title — the method and path. */
    val header: String,
    /** Right-aligned title suffix — the response status. */
    val trailer: String,
    /** Box contents, one line per row. */
    val body: List<String>,
    val edgeLabel: String = "",
)

/** A node's box, placed on the character grid: [col]/[row] top-left, [cols]×[rows] big. */
data class GraphBox(val depth: Int, val col: Int, val row: Int, val cols: Int, val rows: Int)

/** Every box plus the grid extent they occupy. */
data class GraphGeometry(val boxes: Map<String, GraphBox>, val cols: Int, val rows: Int)

/**
 * Id of the synthetic node every traversal root hangs off. It stands for no request — it exists so
 * a session with several entry points (each address-bar send starts one) draws as a single tree
 * rather than a forest of disconnected columns.
 */
const val START_NODE_ID = "__start__"

/**
 * Turns [history] into the boxes the graph draws: a title line over the equivalent curl command,
 * under a single empty [START_NODE_ID] root. A node whose `parentId` is null — or names a node
 * absent from [history] — is parented to start.
 */
fun graphSpecs(history: List<HistoryNode>): List<GraphNodeSpec> {
    if (history.isEmpty()) return emptyList()
    val known = history.mapTo(mutableSetOf()) { it.id }
    val start = GraphNodeSpec(
        id = START_NODE_ID,
        parentId = null,
        header = "start",
        trailer = "",
        body = emptyList(),
    )
    return listOf(start) + history.map { node ->
        GraphNodeSpec(
            id = node.id,
            parentId = node.parentId?.takeIf { it in known } ?: START_NODE_ID,
            header = "${node.method} ${shortenUrl(node.url, 52)}",
            trailer = if (node.response.status == 0) "—" else "${node.response.status}",
            // A node opened inside its parent's document was never requested, so it has no curl.
            body = if (node.originStep != null) {
                listOf("# opened in place from the parent document")
            } else {
                buildCurlCommand(node).lines()
            },
            edgeLabel = node.fromRel.orEmpty(),
        )
    }
}

/**
 * Lays the traversal out as the tidy tree `grapher.sh` draws: **depth** picks the column (each
 * column as wide as its widest box, its gutter as wide as its widest edge label), and children
 * stack down the sibling axis with every parent centred against its first and last child.
 *
 * A node whose `parentId` is null — or names a node absent from [specs] — is treated as a root
 * at depth 0.
 */
fun layoutGraph(specs: List<GraphNodeSpec>): GraphGeometry {
    if (specs.isEmpty()) return GraphGeometry(emptyMap(), 0, 0)

    // Depth, and children in arrival order. Specs come in history order, so a parent is always
    // placed before its children and one pass settles every depth.
    val depth = mutableMapOf<String, Int>()
    val children = mutableMapOf<String, MutableList<String>>()
    specs.forEach { spec ->
        val parent = spec.parentId?.takeIf { depth.containsKey(it) }
        depth[spec.id] = parent?.let { depth.getValue(it) + 1 } ?: 0
        if (parent != null) children.getOrPut(parent) { mutableListOf() }.add(spec.id)
    }

    // Uniform box height (the tallest body plus a header, a rule and padding) keeps the tidy
    // tree's row pitch even; width is per node, clamped to MAX_BOX_COLS.
    val boxRows = specs.maxOf { it.body.size.coerceAtLeast(1) } + 4
    val boxCols = specs.associate { spec ->
        val text = spec.body.map { it.length } + (spec.header.length + spec.trailer.length + 3)
        spec.id to text.max().coerceIn(1, MAX_BOX_COLS) + 4
    }

    // Column x: each column's own width plus a gutter wide enough for the labels crossing it.
    val maxDepth = depth.values.max()
    val colWidth = IntArray(maxDepth + 1)
    val gutter = IntArray(maxDepth + 1) { MIN_EDGE_GAP }
    specs.forEach { spec ->
        val d = depth.getValue(spec.id)
        colWidth[d] = maxOf(colWidth[d], boxCols.getValue(spec.id))
        if (d > 0 && spec.edgeLabel.isNotEmpty()) {
            gutter[d - 1] = maxOf(gutter[d - 1], spec.edgeLabel.length + 6)
        }
    }
    val colX = IntArray(maxDepth + 1)
    for (d in 1..maxDepth) colX[d] = colX[d - 1] + colWidth[d - 1] + gutter[d - 1]

    // Sibling axis: leaves take the next free band, parents centre on their outermost children.
    val top = mutableMapOf<String, Int>()
    var nextRow = 0
    fun place(rootId: String) {
        val stack = ArrayDeque<Pair<String, Boolean>>()
        stack.addLast(rootId to false)
        while (stack.isNotEmpty()) {
            val (id, childrenDone) = stack.removeLast()
            if (top.containsKey(id)) continue
            val kids = children[id].orEmpty()
            when {
                kids.isEmpty() -> {
                    top[id] = nextRow
                    nextRow += boxRows + ROW_GAP
                }
                !childrenDone -> {
                    stack.addLast(id to true)
                    kids.asReversed().forEach { stack.addLast(it to false) }
                }
                else -> top[id] = maxOf(0, (top.getValue(kids.first()) + top.getValue(kids.last())) / 2)
            }
        }
    }
    specs.filter { depth.getValue(it.id) == 0 }.forEach { place(it.id) }
    specs.forEach { if (!top.containsKey(it.id)) place(it.id) }

    val boxes = specs.associate { spec ->
        val d = depth.getValue(spec.id)
        spec.id to GraphBox(
            depth = d,
            col = colX[d],
            row = top.getValue(spec.id),
            cols = boxCols.getValue(spec.id),
            rows = boxRows,
        )
    }
    return GraphGeometry(
        boxes = boxes,
        cols = boxes.values.maxOf { it.col + it.cols },
        rows = boxes.values.maxOf { it.row + it.rows },
    )
}

/** Truncates [s] to [width] characters, marking the cut with an ellipsis. */
internal fun fitCells(s: String, width: Int): String =
    if (s.length <= width) s else s.take((width - 1).coerceAtLeast(0)) + "…"

// ── Canvas ────────────────────────────────────────────────────────────────────────────────────

/**
 * The graph surface of design variant C, drawn the way `grapher.sh --format svg` draws a session:
 * one titled box per request holding its curl command, orthogonal parent→child connectors with an
 * arrowhead and the link rel captioning each hop. Scrolls in both axes; no pan or zoom.
 */
@Composable
fun GraphCanvas(
    history: List<HistoryNode>,
    selectedId: String?,
    loading: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNaHalColors.current
    val specs = remember(history) { graphSpecs(history) }
    val geometry = remember(specs) { layoutGraph(specs) }

    // Cell geometry comes from the font actually in use, so a box sized in characters holds its
    // text exactly whatever monospace face the platform resolves.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charW = remember(measurer, density) {
        val ruler = measurer.measure("0".repeat(40), TextStyle(fontFamily = NaHalMonoFont, fontSize = NaHalDimens.graphTextSize))
        with(density) { (ruler.size.width / 40f).toDp() }.coerceAtLeast(NaHalDimens.graphCharW)
    }
    val rowH = NaHalDimens.graphRowH
    val pad = NaHalDimens.graphPad

    val width = pad * 2 + charW * geometry.cols
    val height = pad * 2 + rowH * geometry.rows

    Box(modifier = modifier.background(c.bg)) {
        when {
            history.isEmpty() && loading -> GraphMessage("connecting…")
            history.isEmpty() -> GraphMessage("Enter a URL in the address bar to start.")
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                Box(modifier = Modifier.size(width, height)) {

                    // Dotted phosphor grid + parent→child connectors.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridStep = NaHalDimens.graphGridStep.toPx()
                        val dot = 0.5.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.04f)
                        var gy = 0f
                        while (gy < size.height) {
                            var gx = 0f
                            while (gx < size.width) {
                                drawCircle(gridColor, radius = dot, center = Offset(gx, gy))
                                gx += gridStep
                            }
                            gy += gridStep
                        }

                        val padPx = pad.toPx()
                        val charPx = charW.toPx()
                        val rowPx = rowH.toPx()
                        val stroke = Stroke(width = 1.4.dp.toPx())
                        val head = 4.dp.toPx()

                        specs.forEach { spec ->
                            val from = geometry.boxes[spec.parentId] ?: return@forEach
                            val to = geometry.boxes[spec.id] ?: return@forEach
                            // Out of the parent's right edge, across a mid-gutter bus, into the
                            // child's left edge — the elbow route the SVG renderer emits.
                            val ox = padPx + (from.col + from.cols) * charPx
                            val oy = padPx + (from.row + from.rows / 2f) * rowPx
                            val ix = padPx + to.col * charPx
                            val iy = padPx + (to.row + to.rows / 2f) * rowPx
                            val mx = (ox + ix) / 2f
                            val color = if (spec.id == selectedId) c.accent else c.border2

                            drawPath(
                                path = Path().apply {
                                    moveTo(ox, oy); lineTo(mx, oy); lineTo(mx, iy); lineTo(ix, iy)
                                },
                                color = color,
                                style = stroke,
                            )
                            drawPath(
                                path = Path().apply {
                                    moveTo(ix, iy)
                                    lineTo(ix - head * 2, iy - head)
                                    lineTo(ix - head * 2, iy + head)
                                    close()
                                },
                                color = color,
                            )
                        }
                    }

                    // Edge labels, centred on the segment entering their target.
                    specs.forEach { spec ->
                        if (spec.edgeLabel.isEmpty()) return@forEach
                        val from = geometry.boxes[spec.parentId] ?: return@forEach
                        val to = geometry.boxes[spec.id] ?: return@forEach
                        val room = to.col - (from.col + from.cols) - 4
                        if (room < 3) return@forEach
                        val label = fitCells(spec.edgeLabel, room)
                        val mx = (from.col + from.cols + to.col) / 2f
                        val anchorX = (mx + to.col) / 2f
                        val anchorY = to.row + to.rows / 2f
                        EdgeLabel(
                            text = label,
                            modifier = Modifier.offset(
                                x = pad + charW * anchorX - charW * label.length / 2 - 3.dp,
                                y = pad + rowH * anchorY - rowH - 4.dp,
                            ),
                        )
                    }

                    // One spec per history node, plus the synthetic start root — which stands for no
                    // request, so it carries no status and cannot be selected.
                    val byId = remember(history) { history.associateBy { it.id } }
                    specs.forEach { spec ->
                        val box = geometry.boxes[spec.id] ?: return@forEach
                        val node = byId[spec.id]
                        GraphNodeBox(
                            spec = spec,
                            box = box,
                            charW = charW,
                            statusColor = node
                                ?.let { nodeStatusColor(it.method, it.response.status) }
                                ?: c.text3,
                            selected = spec.id == selectedId,
                            onPick = if (node != null) onPick else null,
                            modifier = Modifier
                                .offset(x = pad + charW * box.col, y = pad + rowH * box.row)
                                .size(charW * box.cols, rowH * box.rows),
                        )
                    }
                }
            }
        }

        GraphLegend(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

@Composable
private fun GraphNodeBox(
    spec: GraphNodeSpec,
    box: GraphBox,
    charW: Dp,
    statusColor: Color,
    selected: Boolean,
    /** Null for a box that stands for no request — the start root, which stays unclickable. */
    onPick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalNaHalColors.current
    val shape = RoundedCornerShape(NaHalDimens.graphNodeCorner)
    val rowH = NaHalDimens.graphRowH
    // The box is sized to its content in whole cells, two of them padding on each side.
    val textCols = box.cols - 4

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) c.bg3 else c.bg2)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.accent else c.border2,
                shape = shape,
            )
            .then(if (onPick != null) Modifier.clickable { onPick(spec.id) } else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = charW * 2)
                .height(rowH * 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(charW),
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
            Text(
                text = fitCells(spec.header, textCols),
                modifier = Modifier.weight(1f),
                color = if (selected) c.accent else c.text,
                fontSize = NaHalDimens.graphTextSize,
                fontFamily = NaHalMonoFont,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
            Text(
                text = spec.trailer,
                color = statusColor,
                fontSize = NaHalDimens.graphTextSize,
                fontFamily = NaHalMonoFont,
                maxLines = 1,
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.border))

        Text(
            text = spec.body.joinToString("\n") { fitCells(it, textCols) },
            modifier = Modifier.padding(horizontal = charW * 2, vertical = rowH * 0.4f),
            color = c.text2,
            fontSize = NaHalDimens.graphTextSize,
            lineHeight = NaHalDimens.graphLineHeight,
            fontFamily = NaHalMonoFont,
            maxLines = spec.body.size.coerceAtLeast(1),
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

@Composable
private fun EdgeLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalNaHalColors.current
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(c.bg.copy(alpha = 0.9f))
            .padding(horizontal = 3.dp),
        color = c.text3,
        fontSize = 10.sp,
        fontFamily = NaHalMonoFont,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun GraphLegend(modifier: Modifier = Modifier) {
    val c = LocalNaHalColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.bg2)
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendEntry(label = "2xx") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.ok)) }
        LegendEntry(label = "4xx") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.warn)) }
        LegendEntry(label = "5xx") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.err)) }
        LegendEntry(label = "selected") { Box(Modifier.size(10.dp).border(1.dp, c.accent)) }
    }
}

@Composable
private fun LegendEntry(label: String, mark: @Composable () -> Unit) {
    val c = LocalNaHalColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        mark()
        Text(label, color = c.text2, fontSize = 10.5.sp, fontFamily = NaHalMonoFont)
    }
}

@Composable
private fun GraphMessage(text: String) {
    val c = LocalNaHalColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = c.text3, fontSize = 13.sp, fontFamily = NaHalMonoFont)
    }
}
