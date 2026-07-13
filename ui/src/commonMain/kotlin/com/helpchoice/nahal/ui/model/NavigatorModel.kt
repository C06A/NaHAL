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

data class PendingRequest(
    /** Display href shown in the builder/history. For a followed link, [path] drives the actual send. */
    val url: String,
    /** Path to the followed link/property, resolved by core against [rootDocument]. Null for a bare-URL send. */
    val path: ResourcePath? = null,
    /** Document [path] is resolved against (the resource whose links are shown). */
    val rootDocument: HalDocument? = null,
    val templated: Boolean = false,
    val vars: Map<String, String> = emptyMap(),
    val fromRel: String? = null,
    val method: String = "GET",
    val type: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: String = "",
    val parentId: String? = null,
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

fun shortenUrl(url: String, maxLen: Int = 26): String {
    val path = url.replace(Regex("^https?://[^/]+"), "")
    return if (path.length > maxLen) "…${path.takeLast(maxLen - 1)}" else path.ifEmpty { "/" }
}
