package com.helpchoice.nahal.ui.state

import androidx.compose.runtime.*
import com.helpchoice.nahal.core.HalNavigator
import com.helpchoice.nahal.core.RequestSpec
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import com.helpchoice.nahal.ui.model.*
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

@Stable
class NavigatorState(private val scope: CoroutineScope, plugin: HaldishPlugin? = null) {
    private val navigator = if (plugin != null) HalNavigator(plugin) else HalNavigator()

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

    // An exception that escapes a launched coroutine terminates a Kotlin/Native app (the JVM only
    // logs it and keeps running). executeSend already handles navigation errors, but this guards
    // anything thrown outside its try — spec assembly, re-throws — so the native macOS/iOS app
    // stays alive and just clears the busy state instead of crashing.
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        if (e !is CancellationException) {
            println("NaHAL: unhandled coroutine error: ${e.stackTraceToString()}")
            loading = false
            pendingRequest = null
        }
    }

    // A SupervisorJob so one failed request cannot cancel the scope (and thus every later request);
    // parented to the passed scope's Job so it is still torn down when this state leaves composition.
    private val appScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + crashGuard,
    )

    /** Sends [url] as typed in the address bar — a fresh traversal root, not a child of the cursor. */
    fun fetch(url: String): Job =
        appScope.launch { executeSend(PendingRequest(url = url, rootLevel = true)) }

    fun launchSend(req: PendingRequest): Job {
        pendingRequest = null
        return appScope.launch { executeSend(req) }
    }

    private suspend fun executeSend(req: PendingRequest) {
        loading = true
        // An address-bar send is a root; everything else hangs off the node it was launched from,
        // falling back to the cursor when the caller did not name one.
        val parentId = if (req.rootLevel) null else req.parentId ?: current?.id
        val timer = TimeSource.Monotonic.markNow()
        val accept = req.headers["Accept"] ?: req.type ?: HalHttpClient.HAL_ACCEPT
        val effectiveHeaders = if ("Accept" !in req.headers) {
            mapOf("Accept" to accept) + req.headers
        } else req.headers
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
        val spec = if (req.path != null) {
            RequestSpec(
                path = req.path, rootDocument = req.rootDocument,
                method = HttpMethod(req.method), templateVars = req.vars.toTemplateArgs(),
                headers = effectiveHeaders, cookies = req.cookies,
                body = bodyObj, acceptHal = false,
            )
        } else {
            RequestSpec(
                url = req.url, method = HttpMethod(req.method), templateVars = req.vars.toTemplateArgs(),
                headers = effectiveHeaders, cookies = req.cookies,
                body = bodyObj, acceptHal = false,
            )
        }
        try {
            val response = navigator.send(spec)
            val raw = response.raw
            val elapsed = timer.elapsedNow().inWholeMilliseconds
            val document = response.document
            // Final URL after core resolved the link/property and expanded the template.
            val sentUrl = response.url.ifEmpty { req.url }

            appendNode(
                HistoryNode(
                    id = nextId(), url = sentUrl, method = req.method,
                    requestHeaders = effectiveHeaders, requestCookies = req.cookies,
                    requestBody = when {
                        req.method in setOf("GET", "HEAD", "OPTIONS") -> null
                        req.bodyKind == BodyKind.BINARY && req.bodyBytes != null ->
                            "«binary ${req.bodyFileName ?: ""} ${req.bodyBytes.size}B, ${req.bodyContentType}»"
                        req.bodyKind == BodyKind.MULTIPART && req.parts.isNotEmpty() ->
                            "«multipart: ${req.parts.size} part(s)»"
                        else -> req.body.takeIf { it.isNotBlank() }
                    },
                    fromRel = req.fromRel, parentId = parentId,
                    response = FetchedResponse(
                        status = raw.statusCode,
                        statusText = httpStatusText(raw.statusCode),
                        headers = raw.headers.mapValues { (_, v) -> v.joinToString(", ") },
                        cookies = raw.cookies,
                        body = raw.body,
                        document = document,
                        contentType = raw.contentType,
                        bytes = raw.bytes,
                    ),
                    elapsedMs = elapsed,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Report the URL core actually tried (plugins run, template expanded) — the raw href is
            // still CURIE-prefixed / relative, so showing it makes a network failure look like a
            // resolution failure. Falls back to the raw href when resolution is what threw.
            val attemptedUrl = try {
                navigator.resolveUrl(spec).ifEmpty { req.url }
            } catch (_: Throwable) {
                req.url
            }
            appendNode(
                HistoryNode(
                    id = nextId(), url = attemptedUrl, method = req.method,
                    requestHeaders = effectiveHeaders, requestCookies = req.cookies,
                    requestBody = null, fromRel = req.fromRel,
                    parentId = parentId,
                    response = FetchedResponse(
                        status = 0, statusText = "Error",
                        headers = emptyMap(), cookies = emptyMap(),
                        body = e.message ?: "Connection error",
                        document = null,
                        contentType = "text/plain",
                    ),
                    elapsedMs = timer.elapsedNow().inWholeMilliseconds,
                )
            )
        } finally {
            loading = false
            pendingRequest = null
        }
    }

    /**
     * Appends [node] and moves the cursor onto it. The history is **append-only**: it is a
     * traversal *tree* (`parentId`), not a browser back-stack, so sending from a node the user
     * picked earlier branches off it instead of truncating everything that came after. Truncating
     * would drop the siblings the branch was picked from — visible as nodes vanishing from the
     * graph. Back/forward walk the list in arrival order.
     */
    private fun pushNode(node: HistoryNode) {
        history = history + node
        cursor = history.size - 1
    }

    private fun appendNode(node: HistoryNode) {
        pushNode(node)
        requestLog = requestLog + LogEntry(
            id = node.id, url = node.url,
            method = node.method, status = node.response.status,
        )
    }

    fun openEmbedded(parentNode: HistoryNode, rel: String, index: Int) {
        val doc = parentNode.response.document ?: return
        val item = doc.embedded[rel]?.getOrNull(index) ?: return
        // Display label only — opening an embedded resource performs no HTTP request.
        val selfHref = item.links["self"]?.firstOrNull()?.href ?: (current?.url ?: "(embedded:$rel[$index])")

        val node = HistoryNode(
            id = nextId(), url = selfHref, method = "EMBEDDED",
            requestHeaders = emptyMap(), requestCookies = emptyMap(), requestBody = null,
            fromRel = rel, parentId = parentNode.id,
            response = FetchedResponse(
                status = 200, statusText = "Embedded",
                headers = mapOf("Content-Type" to "application/hal+json (embedded)"),
                cookies = emptyMap(), body = item.toRawJson(), document = item,
                contentType = "application/hal+json",
            ),
            elapsedMs = 0, originStep = PathStep.Embedded(rel, index),
        )
        pushNode(node)
    }

    fun openArrayItem(parentNode: HistoryNode, index: Int) {
        val doc = parentNode.response.document ?: return
        val item = doc.items.getOrNull(index) ?: return
        // Display label only — opening an array item performs no HTTP request.
        val selfHref = item.links["self"]?.firstOrNull()?.href ?: (current?.url ?: "item[$index]")
        val node = HistoryNode(
            id = nextId(), url = selfHref, method = "ARRAY",
            requestHeaders = emptyMap(), requestCookies = emptyMap(), requestBody = null,
            fromRel = "[$index]", parentId = parentNode.id,
            response = FetchedResponse(
                status = 200, statusText = "Embedded",
                headers = mapOf("Content-Type" to "application/hal+json (embedded)"),
                cookies = emptyMap(), body = item.toRawJson(), document = item,
                contentType = "application/hal+json",
            ),
            elapsedMs = 0, originStep = PathStep.Item(index),
        )
        pushNode(node)
    }

    /**
     * Prepares a request to follow [link] (the [index]-th link under [rel]) shown on [node]. The UI
     * does no URL manipulation — core resolves the link via its `preLink` plugins and expands the
     * template on send. [link.href] is kept only as the display href until the final URL comes back
     * from core.
     */
    fun prepareRequest(link: HalLink, rel: String, index: Int, node: HistoryNode?) {
        val accept = link.type ?: HalHttpClient.HAL_ACCEPT
        val rooted = rootedPath(node, listOf(PathStep.Link(rel, index)))
        pendingRequest = PendingRequest(
            url = link.href,
            path = rooted?.path ?: ResourcePath.link(rel, index),
            rootDocument = rooted?.rootDocument ?: node?.response?.document,
            templated = link.templated, vars = emptyMap(),
            fromRel = rel, method = "GET", type = link.type,
            headers = mapOf("Accept" to accept),
            cookies = emptyMap(), body = "",
            parentId = node?.id ?: current?.id,
        )
    }

    /**
     * Prepares a request to a URL held in a *property* of [node]'s document, addressed by [terminal]
     * (e.g. `items[0].url`). A property carries no media type, so the request goes to the builder
     * with the HAL `Accept` — the user retypes it if the target is not HAL. [href] is the raw value,
     * shown until core resolves it.
     */
    fun preparePropertyRequest(terminal: List<PathStep.Property>, href: String, node: HistoryNode?) {
        val rooted = rootedPath(node, terminal)
        pendingRequest = PendingRequest(
            url = href,
            path = rooted?.path ?: ResourcePath(terminal),
            rootDocument = rooted?.rootDocument ?: node?.response?.document,
            templated = '{' in href, vars = emptyMap(),
            fromRel = terminal.jsonPathLabel(), method = "GET", type = null,
            headers = mapOf("Accept" to HalHttpClient.HAL_ACCEPT),
            cookies = emptyMap(), body = "",
            parentId = node?.id ?: current?.id,
        )
    }

    /**
     * Prepares a request to [url], the value of response header [name] on [node]. A header is not
     * addressable by a [ResourcePath] (the grammar covers links and properties only), so this is a
     * bare-URL send — the caller passes [url] already resolved to absolute. Like a property, a
     * header carries no media type, so the builder opens with the HAL `Accept`.
     */
    fun prepareHeaderRequest(name: String, url: String, node: HistoryNode?) {
        pendingRequest = PendingRequest(
            url = url,
            templated = '{' in url, vars = emptyMap(),
            fromRel = "header:$name", method = "GET", type = null,
            headers = mapOf("Accept" to HalHttpClient.HAL_ACCEPT),
            cookies = emptyMap(), body = "",
            parentId = node?.id ?: current?.id,
        )
    }

    /**
     * Prepares a request to [profile] — the `profile` value of a link on [node]. RFC 6906 makes it
     * a URI that may or may not dereference; NaHAL follows it like any other URI and lets the
     * response say. Sent **as-is**: none of the holding link's other fields (its `href`, `type`,
     * `templated`) describe the profile resource, so this is a bare-URL send with the HAL `Accept`,
     * and a relative value stays relative for the `preLink` plugins to resolve — the same treatment
     * a typed address gets.
     */
    fun prepareProfileRequest(profile: String, node: HistoryNode?) {
        pendingRequest = PendingRequest(
            url = profile,
            templated = '{' in profile, vars = emptyMap(),
            fromRel = "profile", method = "GET", type = null,
            headers = mapOf("Accept" to HalHttpClient.HAL_ACCEPT),
            cookies = emptyMap(), body = "",
            parentId = node?.id ?: current?.id,
        )
    }

    /**
     * Reopens [node]'s request in the builder — same URL, headers, cookies and body prefilled —
     * so it can be resent with a different method or body. A bare-URL send: [node]'s URL is
     * already the final resolved one, there is no link to re-resolve. A non-text body is stored
     * on the node only as a display placeholder (`«binary …»`), so it prefills empty.
     */
    fun prepareResend(node: HistoryNode) {
        pendingRequest = PendingRequest(
            url = node.url,
            fromRel = node.fromRel, method = node.method,
            headers = node.requestHeaders, cookies = node.requestCookies,
            body = node.requestBody?.takeUnless { it.startsWith("«") } ?: "",
            parentId = node.id,
        )
    }

    /**
     * The path to [terminal] rooted at [node]'s nearest fetched ancestor, so plugins see the whole
     * embedding stack. Null when the chain has no [ResourcePath] representation — callers then
     * address [terminal] against [node]'s own document.
     */
    private fun rootedPath(node: HistoryNode?, terminal: List<PathStep>): RootedPath? =
        node?.let { resolveNodePath(history, it, terminal) }

    fun goBack() { if (canGoBack) cursor-- }
    fun goForward() { if (canGoForward) cursor++ }
    fun jumpTo(id: String) { val idx = history.indexOfFirst { it.id == id }; if (idx >= 0) cursor = idx }
}

/**
 * Expands [template] with [vars], each of which may be a string, a list or an associative array
 * (RFC 6570 §2.3). The three `UriTemplateVars.set` overloads are resolved statically, so the
 * dispatch has to happen here rather than by handing `Any` to one of them — that would pick the
 * scalar overload and stringify the whole collection.
 */
fun expandTemplate(template: String, vars: Map<String, TemplateVarValue>): String {
    val tvars = vars.entries.fold(UriTemplateVars()) { acc, (k, v) ->
        when (val arg = v.toTemplateArg()) {
            is List<*>   -> acc.set(k, arg)
            is Map<*, *> -> acc.set(k, arg.mapKeys { it.key.toString() })
            else         -> acc.set(k, arg)
        }
    }
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
fun rememberNavigatorState(plugin: HaldishPlugin? = null): NavigatorState {
    val scope = rememberCoroutineScope()
    return remember { NavigatorState(scope, plugin) }
}
