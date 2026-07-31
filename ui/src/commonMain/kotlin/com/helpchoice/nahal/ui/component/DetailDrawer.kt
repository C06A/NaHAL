package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.ui.LocalCurrentUrl
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.model.HistoryNode

private enum class DrawerTab(val label: String) {
    Overview("overview"), Links("links"), Embedded("embedded"), Props("props"), Response("response")
}

private enum class ResponseTab(val label: String) { Body("body"), Headers("headers"), Cookies("cookies") }

/**
 * The detail surface of design variant C — five tabs over the node selected in the graph. Each pane
 * reuses the panel the two-pane layout already renders inside its accordion.
 */
@Composable
fun DetailDrawer(
    node: HistoryNode,
    position: Int,
    total: Int,
    onFollow: (rel: String, index: Int, link: HalLink) -> Unit,
    onFollowProfile: (profile: String) -> Unit,
    onFollowProperty: (terminal: List<PathStep.Property>, href: String) -> Unit,
    onFollowHeader: (name: String, url: String) -> Unit,
    onOpenEmbedded: (rel: String, index: Int) -> Unit,
    onOpenArrayItem: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNaHalColors.current
    var tab by remember { mutableStateOf(DrawerTab.Overview) }
    var responseTab by remember { mutableStateOf(ResponseTab.Body) }

    val doc = node.response.document
    val linkCount = doc?.links?.entries?.count { it.key != "curies" } ?: 0
    val embeddedCount = doc?.embedded?.size ?: 0

    Column(modifier = modifier.background(c.bg2)) {

        // ── Header ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg2)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Eyebrow("Selected · node $position/$total")
            SelectionContainer {
                Text(
                    text = node.url,
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = NaHalMonoFont,
                )
            }
        }
        NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        // ── Tabs ──────────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            DrawerTab.entries.forEach { entry ->
                val count = when (entry) {
                    DrawerTab.Links -> linkCount
                    DrawerTab.Embedded -> embeddedCount
                    else -> null
                }
                DrawerTabButton(
                    label = entry.label,
                    count = count,
                    active = tab == entry,
                    onClick = { tab = entry },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        // ── Body ──────────────────────────────────────────────────────────────
        CompositionLocalProvider(LocalCurrentUrl provides node.url) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 14.dp),
            ) {
                when (tab) {
                    DrawerTab.Overview -> OverviewPane(node, linkCount, embeddedCount)

                    DrawerTab.Links ->
                        if (doc == null) EmptyState("No links.")
                        else LinksPanel(
                            document = doc,
                            onFollow = onFollow,
                            onFollowProfile = onFollowProfile,
                        )

                    DrawerTab.Embedded ->
                        if (doc == null) EmptyState("No embedded resources.")
                        else EmbeddedPanel(document = doc, onOpen = onOpenEmbedded)

                    DrawerTab.Props -> when {
                        doc == null -> EmptyState("No properties.")
                        // A top-level JSON array has no properties — its items are what you open.
                        doc.rawBody != null -> ArrayItemsPanel(items = doc.items, onOpen = onOpenArrayItem)
                        else -> PropTree(document = doc, onFollow = onFollowProperty)
                    }

                    DrawerTab.Response -> Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ResponseTab.entries.forEach { entry ->
                                DrawerTabButton(
                                    label = "[${entry.label}]",
                                    count = null,
                                    active = responseTab == entry,
                                    onClick = { responseTab = entry },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        when (responseTab) {
                            ResponseTab.Body -> RawJsonPanel(body = node.response.body)
                            ResponseTab.Headers -> HeadersPanel(node.response.headers, onFollow = onFollowHeader)
                            ResponseTab.Cookies ->
                                if (node.response.cookies.isEmpty()) EmptyState("No Set-Cookie")
                                else CookiesPanel(node.response.cookies)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewPane(node: HistoryNode, linkCount: Int, embeddedCount: Int) {
    val c = LocalNaHalColors.current
    val curies = node.response.document?.links?.get("curies").orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatCard("method", node.method, Modifier.weight(1f))
            StatCard(
                label = "status",
                value = "${node.response.status}",
                modifier = Modifier.weight(1f),
                valueColor = nodeStatusColor(node.method, node.response.status),
            )
            StatCard("elapsed", "${node.elapsedMs}ms", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatCard("links", "$linkCount", Modifier.weight(1f))
            StatCard("embedded", "$embeddedCount", Modifier.weight(1f))
            StatCard("cookies", "${node.response.cookies.size}", Modifier.weight(1f))
        }

        if (curies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            Spacer(Modifier.height(8.dp))
            Eyebrow("Curies")
            curies.forEach { curie ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${curie.name ?: "?"}:",
                        color = c.accent,
                        fontSize = 11.sp,
                        fontFamily = NaHalMonoFont,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = curie.href,
                        color = c.text2,
                        fontSize = 11.sp,
                        fontFamily = NaHalMonoFont,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val c = LocalNaHalColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.bg3)
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = c.text3,
            fontSize = 10.sp,
            fontFamily = NaHalMonoFont,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = value,
            color = valueColor ?: c.text,
            fontSize = 13.sp,
            fontFamily = NaHalMonoFont,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DrawerTabButton(
    label: String,
    count: Int?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNaHalColors.current
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (active) c.text else c.text3,
                fontSize = 11.sp,
                fontFamily = NaHalMonoFont,
            )
            if (count != null) {
                Text(
                    text = "$count",
                    color = (if (active) c.text else c.text3).copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = NaHalMonoFont,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (active) c.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    val c = LocalNaHalColors.current
    Text(
        text = text.uppercase(),
        color = c.text3,
        fontSize = 10.sp,
        fontFamily = NaHalMonoFont,
        letterSpacing = 0.8.sp,
    )
}
