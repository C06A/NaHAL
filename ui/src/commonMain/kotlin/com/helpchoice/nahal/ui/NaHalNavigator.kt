package com.helpchoice.nahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.ui.component.*
import com.helpchoice.nahal.ui.model.*
import com.helpchoice.nahal.ui.state.NavigatorState
import com.helpchoice.nahal.ui.state.rememberNavigatorState

// ── View mode enums ───────────────────────────────────────────────────────────

private enum class ViewKind  { Response, Request }
private enum class ViewMode  { Pretty,   Raw     }

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun NaHalNavigator(startUrl: String = "") {
    val state = rememberNavigatorState()
    NaHalTheme {
        NaHalNavigatorContent(state = state, startUrl = startUrl)
    }
}

@Composable
private fun NaHalNavigatorContent(state: NavigatorState, startUrl: String) {
    val c = LocalNaHalColors.current

    var selectedId by remember { mutableStateOf<String?>(null) }
    var viewKind by remember { mutableStateOf(ViewKind.Response) }
    var viewMode by remember { mutableStateOf(ViewMode.Pretty) }
    var openResp by remember { mutableStateOf(setOf("links", "props", "body", "items")) }
    var openReq  by remember { mutableStateOf(setOf("headers")) }

    // Boot with start URL
    LaunchedEffect(startUrl) {
        if (startUrl.isNotBlank()) state.fetch(startUrl)
    }

    // Auto-select newest node
    val histLen = state.history.size
    val prevLen = remember { mutableStateOf(0) }
    if (histLen != prevLen.value) {
        prevLen.value = histLen
        state.current?.let { selectedId = it.id; viewKind = ViewKind.Response }
    }

    val selectedNode = state.history.find { it.id == selectedId } ?: state.current

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        // Top bar
        NaHalTopBar(
            state = state,
            onNavigate = { url -> state.fetch(url) },
        )

        // Two-pane
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

            // ─ Left rail ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(NaHalDimens.railWidth)
                    .fillMaxHeight()
                    .background(c.bg2)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Traversal header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Traversal")
                    CountBadge(state.history.size)
                }

                TraversalTree(
                    history = state.history,
                    activeId = selectedId,
                    onPick = { id -> selectedId = id; viewKind = ViewKind.Response },
                )

                Spacer(Modifier.height(10.dp))
                NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

                // Request log header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 18.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Request Log")
                    CountBadge(state.requestLog.size)
                }

                RequestLog(
                    log = state.requestLog,
                    activeId = selectedId,
                    onPick = { id -> selectedId = id; viewKind = ViewKind.Request },
                )

                Spacer(Modifier.height(16.dp))
            }

            // ─ Border ────────────────────────────────────────────────────────
            NaHalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            // ─ Center ────────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    // Template form or request builder when pending
                    state.pendingRequest != null -> {
                        val req = state.pendingRequest!!
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            if (req.templated) {
                                TemplateForm(
                                    request = req,
                                    onVarsChange = { vars ->
                                        state.pendingRequest = req.copy(vars = vars)
                                    },
                                    onSubmit = { expanded ->
                                        state.pendingRequest = req.copy(url = expanded, templated = false)
                                    },
                                    onCancel = { state.pendingRequest = null },
                                )
                            } else {
                                RequestBuilder(
                                    request = req,
                                    onChange = { updated -> state.pendingRequest = updated },
                                    onSend = { r -> state.launchSend(r) },
                                    onCancel = { state.pendingRequest = null },
                                )
                            }
                        }
                    }

                    // Loading state (first load)
                    selectedNode == null && state.loading ->
                        CenterMessage("Fetching entry point…")

                    // Empty state
                    selectedNode == null ->
                        CenterMessage("Enter a URL in the address bar to start.")

                    // Main content
                    else -> CenterPanel(
                        state = state,
                        node = selectedNode,
                        viewKind = viewKind,
                        viewMode = viewMode,
                        openResp = openResp,
                        openReq = openReq,
                        onViewKindToggle = {
                            viewKind = if (viewKind == ViewKind.Request) ViewKind.Response else ViewKind.Request
                        },
                        onViewModeToggle = {
                            viewMode = if (viewMode == ViewMode.Pretty) ViewMode.Raw else ViewMode.Pretty
                        },
                        onToggleResp = { k -> openResp = if (k in openResp) openResp - k else openResp + k },
                        onToggleReq  = { k -> openReq  = if (k in openReq)  openReq  - k else openReq  + k },
                        onSelectNode = { id -> selectedId = id },
                        onFollow = { rel, index, link ->
                            state.prepareRequest(
                                link = link,
                                rel = rel,
                                index = index,
                                rootDocument = selectedNode.response.document,
                                parentNodeId = selectedNode.id,
                            )
                        },
                        onOpenEmbedded = { rel, idx ->
                            state.openEmbedded(selectedNode, rel, idx)
                        },
                        onOpenArrayItem = { idx ->
                            state.openArrayItem(selectedNode, idx)
                        },
                    )
                }
            }
        }
    }
}

// ── Center panel ──────────────────────────────────────────────────────────────

@Composable
private fun CenterPanel(
    state: NavigatorState,
    node: HistoryNode,
    viewKind: ViewKind,
    viewMode: ViewMode,
    openResp: Set<String>,
    openReq: Set<String>,
    onViewKindToggle: () -> Unit,
    onViewModeToggle: () -> Unit,
    onToggleResp: (String) -> Unit,
    onToggleReq: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onFollow: (rel: String, index: Int, link: HalLink) -> Unit,
    onOpenEmbedded: (rel: String, idx: Int) -> Unit,
    onOpenArrayItem: (idx: Int) -> Unit,
) {
    val c = LocalNaHalColors.current

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Sticky header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg2)
                .border(width = 1.dp, color = c.border, shape = RoundedCornerShape(0.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Section title
                SectionTitle(if (viewKind == ViewKind.Request) "Request" else "Response")

                // Breadcrumb + status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MethodBadge(node.method)
                    BreadcrumbTrail(
                        node = node,
                        history = state.history,
                        onSelect = onSelectNode,
                        modifier = Modifier.weight(1f),
                    )
                    if (viewKind == ViewKind.Response) {
                        StatusPill(code = node.response.status, statusText = node.response.statusText)
                    }
                }
            }

            // View toggle buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleButton(
                    label = if (viewKind == ViewKind.Request) "Request" else "Response",
                    icon = if (viewKind == ViewKind.Request) "→" else "←",
                    onClick = onViewKindToggle,
                )
                ToggleButton(
                    label = if (viewMode == ViewMode.Pretty) "Pretty" else "Raw",
                    icon = if (viewMode == ViewMode.Pretty) "≡" else "</>",
                    onClick = onViewModeToggle,
                )
            }
        }

        // ── Scrollable body ───────────────────────────────────────────────────
        CompositionLocalProvider(LocalCurrentUrl provides node.url) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                when {
                    viewKind == ViewKind.Response && viewMode == ViewMode.Pretty -> {
                        val doc = node.response.document
                        val sections = buildResponseSections(
                            node = node,
                            doc = doc,
                            onFollow = onFollow,
                            onOpenEmbedded = onOpenEmbedded,
                            onOpenArrayItem = onOpenArrayItem,
                        )
                        Accordion(
                            sections = sections,
                            openSections = openResp,
                            onToggle = onToggleResp,
                        )
                    }
                    viewKind == ViewKind.Response && viewMode == ViewMode.Raw -> {
                        val resp = node.response
                        val statusLine = "HTTP/1.1 ${resp.status} ${resp.statusText}"
                        val headerLines = resp.headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }
                        RawJsonPanel(
                            body = resp.body,
                            preamble = "$statusLine\n$headerLines\n",
                        )
                    }
                    viewKind == ViewKind.Request && viewMode == ViewMode.Pretty -> {
                        val sections = buildRequestSections(node = node)
                        Accordion(
                            sections = sections,
                            openSections = openReq,
                            onToggle = onToggleReq,
                        )
                    }
                    viewKind == ViewKind.Request && viewMode == ViewMode.Raw -> {
                        CurlPanel(node = node)
                    }
                }
            }
        }
    }
}

private fun buildResponseSections(
    node: HistoryNode,
    doc: HalDocument?,
    onFollow: (String, Int, HalLink) -> Unit,
    onOpenEmbedded: (String, Int) -> Unit,
    onOpenArrayItem: (Int) -> Unit,
): List<AccordionSection> = buildList {
    add(AccordionSection(
        key = "headers",
        title = "Headers",
        count = node.response.headers.size,
        content = { HeadersPanel(node.response.headers) },
    ))
    add(AccordionSection(
        key = "cookies",
        title = "Cookies",
        count = node.response.cookies.size,
        content = { CookiesPanel(node.response.cookies) },
    ))
    val rawBody = doc?.rawBody
    val items = doc?.items ?: emptyList()
    if (rawBody != null) {
        add(AccordionSection(
            key = "items",
            title = "Items",
            count = items.size,
            content = { ArrayItemsPanel(items = items, onOpen = onOpenArrayItem) },
        ))
        add(AccordionSection(
            key = "body",
            title = "Body",
            content = { RawJsonPanel(body = rawBody) },
        ))
    } else if (doc != null) {
        val linkCount = doc.links.entries.count { it.key != "curies" }
        add(AccordionSection(
            key = "links",
            title = "Links",
            count = linkCount,
            content = { LinksPanel(document = doc, onFollow = onFollow) },
        ))
        add(AccordionSection(
            key = "embedded",
            title = "Embedded",
            count = doc.embedded.size,
            content = { EmbeddedPanel(document = doc, onOpen = onOpenEmbedded) },
        ))
        add(AccordionSection(
            key = "props",
            title = "Properties",
            count = doc.properties.size,
            content = { PropTree(document = doc) },
        ))
    } else if (node.response.body.isNotBlank()) {
        add(AccordionSection(
            key = "body",
            title = "Body",
            content = { RawJsonPanel(body = node.response.body) },
        ))
    }
}

private fun buildRequestSections(node: HistoryNode): List<AccordionSection> = buildList {
    add(AccordionSection(
        key = "line",
        title = "Method · URL",
        content = {
            val c = LocalNaHalColors.current
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MethodBadge(node.method)
                    Text(
                        text = node.url,
                        color = c.text,
                        fontSize = 12.5.sp,
                        fontFamily = NaHalMonoFont,
                    )
                }
                node.fromRel?.let {
                    Text(
                        text = "from rel: $it",
                        color = c.text3,
                        fontSize = 10.sp,
                        fontFamily = NaHalMonoFont,
                    )
                }
            }
        },
    ))
    add(AccordionSection(
        key = "headers",
        title = "Headers",
        count = node.requestHeaders.size,
        content = { HeadersPanel(node.requestHeaders) },
    ))
    add(AccordionSection(
        key = "cookies",
        title = "Cookies",
        count = node.requestCookies.size,
        content = { CookiesPanel(node.requestCookies) },
    ))
    add(AccordionSection(
        key = "body",
        title = "Body",
        subtitle = if (node.requestBody == null) "— empty —" else null,
        content = {
            if (node.requestBody != null) {
                RawJsonPanel(body = node.requestBody)
            } else {
                EmptyState("No body.")
            }
        },
    ))
}

// ── Breadcrumb trail ──────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbTrail(
    node: HistoryNode,
    history: List<HistoryNode>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNaHalColors.current
    val chain = buildChain(node, history)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chain.forEachIndexed { i, chainNode ->
            if (i > 0) {
                Text("›", color = c.text3.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            val isCurrent = chainNode.id == node.id
            Text(
                text = chainNode.fromRel ?: "root",
                color = if (isCurrent) c.text else c.text3,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                fontFamily = NaHalMonoFont,
                modifier = Modifier
                    .then(if (!isCurrent) Modifier.clickable { onSelect(chainNode.id) } else Modifier)
                    .clip(RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

private fun buildChain(node: HistoryNode, history: List<HistoryNode>): List<HistoryNode> {
    val byId = history.associateBy { it.id }
    val chain = mutableListOf<HistoryNode>()
    var current: HistoryNode? = node
    var guard = 0
    while (current != null && guard++ < 64) {
        chain.add(0, current)
        current = current.parentId?.let { byId[it] }
    }
    return chain
}

// ── Toggle button ─────────────────────────────────────────────────────────────

@Composable
private fun ToggleButton(label: String, icon: String, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.bg2)
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(icon, color = c.text3, fontSize = 12.sp, fontFamily = NaHalMonoFont)
        Text(label, color = c.text2, fontSize = 11.sp, fontFamily = NaHalMonoFont, fontWeight = FontWeight.Medium)
    }
}

// ── Curl panel ────────────────────────────────────────────────────────────────

@Composable
private fun CurlPanel(node: HistoryNode) {
    val c = LocalNaHalColors.current
    val curl = buildCurlCommand(node)
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text = curl,
            fontFamily = NaHalMonoFont,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = c.text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(c.bg2)
                .border(1.dp, c.border, RoundedCornerShape(5.dp))
                .padding(16.dp),
        )
    }
}

private fun buildCurlCommand(node: HistoryNode): String {
    val parts = mutableListOf("curl -X ${node.method}", "  '${node.url}'")
    node.requestHeaders.forEach { (k, v) ->
        if (k.isNotBlank()) parts.add("  -H '$k: $v'")
    }
    node.requestCookies.forEach { (k, v) ->
        if (k.isNotBlank()) parts.add("  -b '$k=$v'")
    }
    node.requestBody?.let { body ->
        if (node.method !in setOf("GET", "HEAD", "OPTIONS")) {
            parts.add("  --data-raw '$body'")
        }
    }
    return parts.joinToString(" \\\n")
}

// ── Request log ───────────────────────────────────────────────────────────────

@Composable
private fun RequestLog(
    log: List<LogEntry>,
    activeId: String?,
    onPick: (String) -> Unit,
) {
    val c = LocalNaHalColors.current
    if (log.isEmpty()) { EmptyState("No requests yet."); return }

    Column {
        log.asReversed().forEach { entry ->
            val isActive = entry.id == activeId
            val sc = statusClass(entry.status)
            val statusColor = when (sc) {
                StatusClass.OK    -> c.ok
                StatusClass.WARN  -> c.warn
                StatusClass.ERR   -> c.err
                StatusClass.REDIR -> c.redir
                StatusClass.INFO  -> c.info
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isActive) c.accentSoft else c.bg2)
                    .border(
                        width = 2.dp,
                        color = if (isActive) c.accent else c.bg2,
                        shape = RoundedCornerShape(0.dp),
                    )
                    .clickable { onPick(entry.id) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MethodBadge(entry.method)
                Text(
                    text = "${entry.status}",
                    color = statusColor,
                    fontSize = 11.sp,
                    fontFamily = NaHalMonoFont,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = shortenUrl(entry.url),
                    color = c.text2,
                    fontSize = 11.sp,
                    fontFamily = NaHalMonoFont,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Center message ────────────────────────────────────────────────────────────

@Composable
private fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val c = LocalNaHalColors.current
        Text(text, color = c.text3, fontSize = 13.sp, fontFamily = NaHalMonoFont)
    }
}
