package com.helpchoice.nahal.ui.model

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath

data class HistoryNode(
    val id: String,
    val url: String,
    val method: String,
    val requestHeaders: Map<String, String>,
    val requestCookies: Map<String, String>,
    val requestBody: String?,
    val fromRel: String?,
    val parentId: String?,
    val response: FetchedResponse,
    val elapsedMs: Long,
    /**
     * The step that produced this node from its parent, when it was opened *within* the parent's
     * document rather than fetched: [PathStep.Embedded] for an embedded sub-resource,
     * [PathStep.Item] for a top-level array item. Null for a fetched node — whose document is
     * therefore a root a [ResourcePath] can be built against.
     */
    val originStep: PathStep? = null,
)

data class FetchedResponse(
    val status: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
    val body: String,
    val document: HalDocument?,
    /** Raw Content-Type header value; may carry parameters (";charset=..."). */
    val contentType: String? = null,
    /** Wire bytes of the body — array identity, not content, drives [equals]; the UI never compares instances. */
    val bytes: ByteArray = body.encodeToByteArray(),
)

/** How the request body is built in the request editor. */
enum class BodyKind { TEXT, BINARY, MULTIPART }

/** One multipart entry — a key-value [field][isFile]=false or a picked file [isFile]=true. */
data class BodyPart(
    val name: String = "",
    val isFile: Boolean = false,
    val value: String = "",                       // field value (text)
    val fileName: String? = null,                 // file part
    val bytes: ByteArray? = null,                 // file part contents
    val contentType: String = "text/plain",
)

/**
 * One row of a URI Template variable's value. [key] carries the name half of an associative-array
 * entry and is meaningless — never read — while [TemplateVarValue.keyed] is false.
 */
data class VarRow(val key: String = "", val value: String = "")

/**
 * A URI Template variable's value. RFC 6570 §2.3 allows three kinds, and the editor expresses all
 * three with one shape — rows, plus whether the key column is showing:
 *
 * | rows | keyed | kind |
 * |---|---|---|
 * | 1 | false | string (`{id}` → `42`) |
 * | 2+ | false | list (`{/segs*}` → `/a/b`) |
 * | any | true | associative array (`{?pairs*}` → `?x=1&y=2`) |
 *
 * A single row therefore expands as a scalar, not a one-element list — a list of one is
 * indistinguishable from a string in every operator RFC 6570 defines, so nothing is lost.
 */
data class TemplateVarValue(
    val rows: List<VarRow> = listOf(VarRow()),
    val keyed: Boolean = false,
) {
    /**
     * The value in the form core and haldish expect: `String`, `List<String>`, or
     * `Map<String, String>` — dispatched on by `HalNavigator`'s `toUriTemplateVars`.
     *
     * Blank keys drop out of the associative form: an unnamed entry has no expansion under any
     * operator, and passing it through would emit a stray `=value` pair.
     */
    fun toTemplateArg(): Any = when {
        keyed          -> rows.filter { it.key.isNotBlank() }.associate { it.key to it.value }
        rows.size == 1 -> rows.first().value
        else           -> rows.map { it.value }
    }

    /** True once the value is anything a plain text field could not express. */
    val isCompound: Boolean get() = keyed || rows.size > 1

    companion object {
        fun scalar(value: String): TemplateVarValue = TemplateVarValue(listOf(VarRow(value = value)))
    }
}

/** `String` / `List` / `Map` values keyed by variable name, ready for `RequestSpec.templateVars`. */
fun Map<String, TemplateVarValue>.toTemplateArgs(): Map<String, Any> =
    mapValues { (_, v) -> v.toTemplateArg() }

data class PendingRequest(
    /** Display href shown in the builder/history. For a followed link, [path] drives the actual send. */
    val url: String,
    /** Path to the followed link/property, resolved by core against [rootDocument]. Null for a bare-URL send. */
    val path: ResourcePath? = null,
    /** Document [path] is resolved against (the resource whose links are shown). */
    val rootDocument: HalDocument? = null,
    val templated: Boolean = false,
    /** Values typed into the template form, by variable name. Scalar, list or associative. */
    val vars: Map<String, TemplateVarValue> = emptyMap(),
    val fromRel: String? = null,
    val method: String = "GET",
    val type: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: String = "",
    val parentId: String? = null,
    /**
     * A send typed into the address bar: it starts a new traversal rather than continuing the
     * selected one, so the node lands at the root with no parent (the graph hangs every root off
     * its synthetic start node). Without this the node would inherit the cursor's id as its parent.
     */
    val rootLevel: Boolean = false,
    // ── non-text bodies ──
    val bodyKind: BodyKind = BodyKind.TEXT,
    val bodyBytes: ByteArray? = null,             // BINARY body
    val bodyFileName: String? = null,
    val bodyContentType: String = "application/octet-stream",
    val parts: List<BodyPart> = emptyList(),      // MULTIPART body
)

data class LogEntry(
    val id: String,
    val url: String,
    val method: String,
    val status: Int,
)

enum class StatusClass { OK, WARN, ERR, REDIR, INFO }

fun statusClass(code: Int): StatusClass = when {
    code == 0    -> StatusClass.ERR
    code >= 500  -> StatusClass.ERR
    code >= 400  -> StatusClass.WARN
    code >= 300  -> StatusClass.REDIR
    code >= 200  -> StatusClass.OK
    else         -> StatusClass.INFO
}

fun httpStatusText(code: Int): String = when (code) {
    200 -> "OK";           201 -> "Created";            202 -> "Accepted"
    204 -> "No Content";   301 -> "Moved Permanently";  302 -> "Found"
    304 -> "Not Modified"; 400 -> "Bad Request";         401 -> "Unauthorized"
    403 -> "Forbidden";    404 -> "Not Found";           405 -> "Method Not Allowed"
    409 -> "Conflict";     422 -> "Unprocessable Entity"; 429 -> "Too Many Requests"
    500 -> "Internal Server Error"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"
    else -> ""
}

/** The equivalent `curl` invocation for [node], one flag per continued line. */
fun buildCurlCommand(node: HistoryNode): String {
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

fun shortenUrl(url: String, maxLen: Int = 26): String {
    val path = url.replace(Regex("^https?://[^/]+"), "")
    return if (path.length > maxLen) "…${path.takeLast(maxLen - 1)}" else path.ifEmpty { "/" }
}
