package com.helpchoice.nahal.ui.state

import androidx.compose.runtime.*
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.parser.HalParser
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import com.helpchoice.nahal.ui.model.*
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

@Stable
class NavigatorState(private val scope: CoroutineScope) {
    private val client = HalHttpClient()

    var history by mutableStateOf<List<HistoryNode>>(emptyList())
        private set
    var cursor by mutableStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set
    var pendingRequest by mutableStateOf<PendingRequest?>(null)
    var requestLog by mutableStateOf<List<LogEntry>>(emptyList())
        private set

    val current: HistoryNode? get() = history.getOrNull(cursor)
    val canGoBack: Boolean get() = cursor > 0
    val canGoForward: Boolean get() = cursor < history.size - 1

    private var idCounter = 0
    private fun nextId() = "n${++idCounter}"

    fun fetch(url: String): Job = scope.launch { executeSend(PendingRequest(url = url)) }

    fun launchSend(req: PendingRequest): Job {
        pendingRequest = null
        return scope.launch { executeSend(req) }
    }

    private suspend fun executeSend(req: PendingRequest) {
        loading = true
        val timer = TimeSource.Monotonic.markNow()
        val accept = req.headers["Accept"] ?: req.type ?: HalHttpClient.HAL_ACCEPT
        val effectiveHeaders = if ("Accept" !in req.headers) {
            mapOf("Accept" to accept) + req.headers
        } else req.headers
        try {
            val bodyObj: HalRequestBody = when {
                req.method in setOf("GET", "HEAD", "OPTIONS") -> HalRequestBody.None
                req.bodyKind == BodyKind.BINARY && req.bodyBytes != null ->
                    HalRequestBody.Binary(req.bodyBytes, req.bodyContentType)
                req.bodyKind == BodyKind.MULTIPART && req.parts.isNotEmpty() ->
                    HalRequestBody.Multipart(req.parts.map { p ->
                        if (p.isFile) MultipartPart(p.name, p.bytes ?: ByteArray(0),
                            fileName = p.fileName, contentType = p.contentType)
                        else MultipartPart(p.name, p.value.encodeToByteArray(),
                            fileName = null, contentType = p.contentType)
                    })
                req.body.isNotBlank() -> HalRequestBody.Text(req.body, "application/json")
                else -> HalRequestBody.None
            }
            val raw = client.execute(
                HalHttpRequest(
                    url = req.url,
                    method = HttpMethod(req.method),
                    headers = effectiveHeaders,
                    cookies = req.cookies,
                    body = bodyObj,
                    acceptHal = false,
                )
            )
            val elapsed = timer.elapsedNow().inWholeMilliseconds
            val document = if (raw.isHal) {
                runCatching { HalParser.parse(raw.body, raw.contentType) }.getOrNull()
            } else null

            appendNode(
                HistoryNode(
                    id = nextId(), url = req.url, method = req.method,
                    requestHeaders = effectiveHeaders, requestCookies = req.cookies,
                    requestBody = when {
                        req.method in setOf("GET", "HEAD", "OPTIONS") -> null
                        req.bodyKind == BodyKind.BINARY && req.bodyBytes != null ->
                            "«binary ${req.bodyFileName ?: ""} ${req.bodyBytes.size}B, ${req.bodyContentType}»"
                        req.bodyKind == BodyKind.MULTIPART && req.parts.isNotEmpty() ->
                            "«multipart: ${req.parts.size} part(s)»"
                        else -> req.body.takeIf { it.isNotBlank() }
                    },
                    fromRel = req.fromRel, parentId = req.parentId ?: current?.id,
                    response = FetchedResponse(
                        status = raw.statusCode,
                        statusText = httpStatusText(raw.statusCode),
                        headers = raw.headers.mapValues { (_, v) -> v.joinToString(", ") },
                        cookies = raw.cookies,
                        body = raw.body,
                        document = document,
                    ),
                    elapsedMs = elapsed,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            appendNode(
                HistoryNode(
                    id = nextId(), url = req.url, method = req.method,
                    requestHeaders = effectiveHeaders, requestCookies = req.cookies,
                    requestBody = null, fromRel = req.fromRel,
                    parentId = req.parentId ?: current?.id,
                    response = FetchedResponse(
                        status = 0, statusText = "Error",
                        headers = emptyMap(), cookies = emptyMap(),
                        body = e.message ?: "Connection error",
                        document = null,
                    ),
                    elapsedMs = timer.elapsedNow().inWholeMilliseconds,
                )
            )
        } finally {
            loading = false
            pendingRequest = null
        }
    }

    private fun appendNode(node: HistoryNode) {
        val trimmed = if (cursor >= 0) history.subList(0, cursor + 1) else emptyList()
        history = trimmed + node
        cursor = history.size - 1
        requestLog = requestLog + LogEntry(
            id = node.id, url = node.url,
            method = node.method, status = node.response.status,
        )
    }

    fun openEmbedded(parentNode: HistoryNode, rel: String, index: Int) {
        val doc = parentNode.response.document ?: return
        val item = doc.embedded[rel]?.getOrNull(index) ?: return
        // Resolve the self href against the parent's absolute URL while cursor still points there.
        val rawHref = item.links["self"]?.firstOrNull()?.href
        val selfHref = if (rawHref != null) resolveHref(rawHref) else (current?.url ?: "(embedded:$rel[$index])")

        val node = HistoryNode(
            id = nextId(), url = selfHref, method = "EMBEDDED",
            requestHeaders = emptyMap(), requestCookies = emptyMap(), requestBody = null,
            fromRel = rel, parentId = parentNode.id,
            response = FetchedResponse(
                status = 200, statusText = "Embedded",
                headers = mapOf("Content-Type" to "application/hal+json (embedded)"),
                cookies = emptyMap(), body = item.toRawJson(), document = item,
            ),
            elapsedMs = 0, embeddedRef = EmbeddedRef(rel, index),
        )
        val trimmed = if (cursor >= 0) history.subList(0, cursor + 1) else emptyList()
        history = trimmed + node
        cursor = history.size - 1
    }

    fun openArrayItem(parentNode: HistoryNode, index: Int) {
        val doc = parentNode.response.document ?: return
        val item = doc.items.getOrNull(index) ?: return
        val rawHref = item.links["self"]?.firstOrNull()?.href
        val selfHref = if (rawHref != null) resolveHref(rawHref) else (current?.url ?: "item[$index]")
        val node = HistoryNode(
            id = nextId(), url = selfHref, method = "ARRAY",
            requestHeaders = emptyMap(), requestCookies = emptyMap(), requestBody = null,
            fromRel = "[$index]", parentId = parentNode.id,
            response = FetchedResponse(
                status = 200, statusText = "Embedded",
                headers = mapOf("Content-Type" to "application/hal+json (embedded)"),
                cookies = emptyMap(), body = item.toRawJson(), document = item,
            ),
            elapsedMs = 0, embeddedRef = EmbeddedRef("items", index),
        )
        val trimmed = if (cursor >= 0) history.subList(0, cursor + 1) else emptyList()
        history = trimmed + node
        cursor = history.size - 1
    }

    fun resolveHref(href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val base = current?.url ?: return href
        return when {
            href.startsWith("//") -> {
                val scheme = base.substringBefore("://")
                "$scheme:$href"
            }
            href.startsWith("/") -> {
                val schemeEnd = base.indexOf("://") + 3
                val hostEnd = base.indexOf("/", schemeEnd).let { if (it < 0) base.length else it }
                base.substring(0, hostEnd) + href
            }
            else -> {
                val schemeEnd = base.indexOf("://") + 3
                val pathStart = base.indexOf("/", schemeEnd)
                val origin = if (pathStart < 0) base else base.substring(0, pathStart)
                val basePath = if (pathStart < 0) "/" else base.substring(pathStart)
                val baseDir = basePath.substringBeforeLast("/") + "/"
                origin + baseDir + href
            }
        }
    }

    fun prepareRequest(href: String, rel: String, templated: Boolean, type: String?, parentNodeId: String?) {
        val accept = type ?: HalHttpClient.HAL_ACCEPT
        pendingRequest = PendingRequest(
            url = resolveHref(href), templated = templated, vars = emptyMap(),
            fromRel = rel, method = "GET", type = type,
            headers = mapOf("Accept" to accept),
            cookies = emptyMap(), body = "",
            parentId = parentNodeId ?: current?.id,
        )
    }

    fun goBack() { if (canGoBack) cursor-- }
    fun goForward() { if (canGoForward) cursor++ }
    fun jumpTo(id: String) { val idx = history.indexOfFirst { it.id == id }; if (idx >= 0) cursor = idx }
}

fun expandTemplate(template: String, vars: Map<String, String>): String {
    val tvars = vars.entries.fold(UriTemplateVars()) { acc, (k, v) -> acc.set(k, v) }
    return UriTemplate(template).expand(tvars)
}

fun extractTemplateVars(template: String): List<String> {
    val regex = Regex("""\{[+#./;?&]?([^}]+)\}""")
    return regex.findAll(template)
        .flatMap { it.groupValues[1].split(",") }
        .map { it.trimEnd('*') }
        .distinct()
        .toList()
}

private val prettyJson = Json { prettyPrint = true }

private fun HalDocument.toRawJson(): String =
    prettyJson.encodeToString(JsonObject.serializer(), toJsonObject())

private fun HalDocument.toJsonObject(): JsonObject = buildJsonObject {
    if (links.isNotEmpty()) {
        put("_links", buildJsonObject {
            links.forEach { (rel, list) ->
                val elems = list.map { it.toJsonObject() }
                if (elems.size == 1) put(rel, elems[0]) else put(rel, JsonArray(elems))
            }
        })
    }
    if (embedded.isNotEmpty()) {
        put("_embedded", buildJsonObject {
            embedded.forEach { (rel, docs) ->
                val elems = docs.map { it.toJsonObject() }
                if (elems.size == 1) put(rel, elems[0]) else put(rel, JsonArray(elems))
            }
        })
    }
    properties.forEach { (key, value) -> put(key, value) }
}

private fun HalLink.toJsonObject(): JsonObject = buildJsonObject {
    put("href", href)
    if (templated) put("templated", true)
    type?.let { put("type", it) }
    name?.let { put("name", it) }
    title?.let { put("title", it) }
    hreflang?.let { put("hreflang", it) }
    profile?.let { put("profile", it) }
    deprecation?.let { put("deprecation", it) }
}

@Composable
fun rememberNavigatorState(): NavigatorState {
    val scope = rememberCoroutineScope()
    return remember { NavigatorState(scope) }
}
