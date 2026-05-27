package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.model.HistoryNode
import com.helpchoice.nahal.ui.model.StatusClass
import com.helpchoice.nahal.ui.model.shortenUrl
import com.helpchoice.nahal.ui.model.statusClass

private data class TreeEntry(
    val node: HistoryNode,
    val children: MutableList<TreeEntry> = mutableListOf(),
)

private fun buildTree(history: List<HistoryNode>): List<TreeEntry> {
    val byId = history.associate { it.id to TreeEntry(it) }
    val roots = mutableListOf<TreeEntry>()
    history.forEach { n ->
        val entry = byId[n.id] ?: return@forEach
        if (n.parentId != null && byId.containsKey(n.parentId)) {
            byId[n.parentId]!!.children.add(entry)
        } else {
            roots.add(entry)
        }
    }
    return roots
}

@Composable
fun TraversalTree(
    history: List<HistoryNode>,
    activeId: String?,
    onPick: (String) -> Unit,
) {
    val roots = remember(history) { buildTree(history) }
    if (roots.isEmpty()) {
        EmptyState("No requests yet.")
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        roots.forEachIndexed { i, root ->
            TreeRow(
                entry = root,
                activeId = activeId,
                onPick = onPick,
                prefix = emptyList(),
                isLast = i == roots.size - 1,
            )
        }
    }
}

@Composable
private fun TreeRow(
    entry: TreeEntry,
    activeId: String?,
    onPick: (String) -> Unit,
    prefix: List<Boolean>, // true = has more siblings (draw vertical line), false = last
    isLast: Boolean,
) {
    val c = LocalNaHalColors.current
    val node = entry.node
    val isActive = node.id == activeId
    val sc = statusClass(node.response.status)
    val statusColor = when (node.method) {
        "EMBEDDED" -> c.redir.copy(alpha = 0.45f)
        "ARRAY"    -> c.accent
        else       -> when (sc) {
            StatusClass.OK    -> c.ok
            StatusClass.WARN  -> c.warn
            StatusClass.ERR   -> c.err
            StatusClass.REDIR -> c.redir
            StatusClass.INFO  -> c.info
        }
    }

    val rowBg = if (isActive) c.accentSoft else Color.Transparent
    val rowBorder = if (isActive) c.accent.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(3.dp))
            .clickable { onPick(node.id) }
            .padding(vertical = 3.dp, horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rail connectors
        Row(modifier = Modifier.height(18.dp)) {
            prefix.forEach { hasMoreSiblings ->
                RailSegment(type = if (hasMoreSiblings) RailType.Vertical else RailType.Empty, color = c.border2)
            }
            RailSegment(type = if (isLast) RailType.CornerLast else RailType.CornerMid, color = c.border2)
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Status dot
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))

        Spacer(modifier = Modifier.width(6.dp))

        // Method label
        val methodLabel = when (node.method) { "EMBEDDED" -> "EMB"; "ARRAY" -> "ARR"; else -> node.method }
        Text(
            text = methodLabel,
            fontSize = 9.5.sp,
            fontFamily = NaHalMonoFont,
            fontWeight = FontWeight.SemiBold,
            color = c.text2,
            modifier = Modifier.width(28.dp),
        )

        // URL / rel label
        val label = node.fromRel ?: shortenUrl(node.url)
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            color = if (isActive) c.text else c.text,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Status code
        Text(
            text = "${node.response.status}",
            fontSize = 10.sp,
            fontFamily = NaHalMonoFont,
            color = statusColor,
            modifier = Modifier.padding(end = 6.dp),
        )
    }

    // Children
    entry.children.forEachIndexed { i, child ->
        TreeRow(
            entry = child,
            activeId = activeId,
            onPick = onPick,
            prefix = prefix + !isLast,
            isLast = i == entry.children.size - 1,
        )
    }
}

private enum class RailType { Vertical, CornerMid, CornerLast, Empty }

@Composable
private fun RailSegment(type: RailType, color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp, 18.dp)
            .drawBehind {
                val sw = 1.dp.toPx()
                val cx = 6.dp.toPx()
                val midY = 9.dp.toPx()
                when (type) {
                    RailType.Vertical -> drawLine(color, Offset(cx, 0f), Offset(cx, size.height), sw)
                    RailType.CornerMid -> {
                        drawLine(color, Offset(cx, 0f), Offset(cx, size.height), sw)
                        drawLine(color, Offset(cx, midY), Offset(size.width, midY), sw)
                    }
                    RailType.CornerLast -> {
                        drawLine(color, Offset(cx, 0f), Offset(cx, midY), sw)
                        drawLine(color, Offset(cx, midY), Offset(size.width, midY), sw)
                    }
                    RailType.Empty -> {}
                }
            }
    )
}
