package com.helpchoice.nahal.plugin.logger

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HALDiSh plugin that records every HTTP exchange as a set of named files.
 *
 * For each request/response pair the plugin writes to [directory]:
 *
 * | File | Content |
 * |------|---------|
 * | `<base>.curl` | Equivalent `curl` command for the request |
 * | `<base>.url` | Final URL the request was sent to |
 * | `<base>.code` | HTTP status code (number only, e.g. `200`) |
 * | `<base>.status` | Status code + reason phrase (e.g. `200 (OK)`) |
 * | `<base>.headers` | Response headers, one `Name: value` per line |
 * | `<base>.body` | Raw response body as received |
 * | `<base>.<fmt>` | Pretty-printed body (`.json`, `.xml`, `.yaml`, `.html`, `.txt`, `.dat`) |
 *
 * The base name has the form `yyyyMMddTHHmmss_NNN` (local-time ISO stamp + zero-padded
 * per-instance counter), e.g. `20260523T143012_001`.
 *
 * ### Important: single-request-at-a-time
 * `LoggerPlugin` stores per-request state in instance fields and is **not safe for
 * concurrent use**. For concurrent scenarios either create one `LoggerPlugin` per
 * `HalHttpClient`, or wrap it with external synchronisation.
 *
 * ### Usage
 * ```kotlin
 * val client = HalHttpClient(
 *     pluginOverride = LoggerPlugin(directory = "/tmp/hal-log")
 * )
 * // or combine with other plugins:
 * val client = HalHttpClient(
 *     pluginOverride = ChainPlugin(
 *         BearerTokenPlugin(token = myToken),
 *         LoggerPlugin(directory = "./hal-log"),   // logger goes last — sees final request
 *     )
 * )
 * ```
 *
 * @param directory Directory where log files are written. Created automatically if absent.
 *                  Defaults to `./haldish-log`.
 */
class LoggerPlugin(val directory: String = "./haldish-log") : HaldishPlugin {

    private var counter: Int = 0
    private var pendingRequest: HalHttpRequest? = null
    private var pendingBaseName: String = ""

    // ── preRequest ────────────────────────────────────────────────────────────

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        platformMakeDir(directory)
        counter++
        val baseName = "${platformCurrentTimestamp()}_${counter.toString().padStart(3, '0')}"
        pendingBaseName = baseName
        pendingRequest  = request
        return request
    }

    // ── postResponse ──────────────────────────────────────────────────────────

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
        val base = pendingBaseName
        val req  = pendingRequest ?: return document
        pendingRequest = null

        val sep = "/"    // platform-neutral path separator for the plugin's internal use

        platformWriteFile("$directory$sep$base.curl",    buildCurlCommand(req))
        platformWriteFile("$directory$sep$base.url",     req.url)
        platformWriteFile("$directory$sep$base.code",    response.statusCode.toString())
        platformWriteFile("$directory$sep$base.status",  "${response.statusCode} (${httpReason(response.statusCode)})")
        platformWriteFile("$directory$sep$base.headers", buildHeaders(response.headers))
        platformWriteFile("$directory$sep$base.body",    response.body)

        val (ext, pretty) = prettyPrint(response.body, response.contentType)
        platformWriteFile("$directory$sep$base.$ext", pretty)

        return document
    }

    // ── curl command builder ──────────────────────────────────────────────────

    private fun buildCurlCommand(req: HalHttpRequest): String {
        val sb = StringBuilder("curl")
        if (req.method.value != "GET") sb.append(" -X ${req.method.value}")
        sb.append(" '${req.url.replace("'", "\\'")}'")
        if (req.acceptHal) {
            sb.append(" \\\n  -H 'Accept: application/hal+json," +
                      " application/hal+xml;q=0.9," +
                      " application/hal+yaml;q=0.8," +
                      " application/json;q=0.7," +
                      " application/xml;q=0.6'")
        }
        req.headers.forEach { (k, v) ->
            sb.append(" \\\n  -H '$k: ${v.replace("'", "\\'")}'")
        }
        if (req.cookies.isNotEmpty()) {
            val cookieStr = req.cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
            sb.append(" \\\n  -H 'Cookie: $cookieStr'")
        }
        when (val body = req.body) {
            is HalRequestBody.None       -> { /* nothing */ }
            is HalRequestBody.Text       -> {
                sb.append(" \\\n  -H 'Content-Type: ${body.contentType}'")
                sb.append(" \\\n  -d '${body.content.replace("'", "\\'")}'")
            }
            is HalRequestBody.Json       -> {
                sb.append(" \\\n  -H 'Content-Type: application/json'")
                sb.append(" \\\n  -d '${body.content.replace("'", "\\'")}'")
            }
            is HalRequestBody.UrlEncoded -> {
                val encoded = body.params.entries.joinToString("&") { (k, v) -> "$k=$v" }
                sb.append(" \\\n  -H 'Content-Type: application/x-www-form-urlencoded'")
                sb.append(" \\\n  -d '$encoded'")
            }
            is HalRequestBody.Binary     -> {
                sb.append(" \\\n  # Binary body: ${body.bytes.size} bytes, ${body.contentType}")
            }
            is HalRequestBody.FilePath   -> {
                sb.append(" \\\n  -H 'Content-Type: ${body.contentType}'")
                sb.append(" \\\n  --data-binary '@${body.path}'")
            }
            is HalRequestBody.Multipart  -> {
                body.parts.forEach { part ->
                    if (part.fileName != null) {
                        sb.append(" \\\n  -F '${part.name}=@${part.fileName};type=${part.contentType}'")
                    } else {
                        sb.append(" \\\n  -F '${part.name}=<binary:${part.bytes.size}bytes>'")
                    }
                }
            }
        }
        return sb.toString()
    }

    // ── header formatter ──────────────────────────────────────────────────────

    private fun buildHeaders(headers: Map<String, List<String>>): String =
        headers.entries.joinToString("\n") { (name, values) ->
            values.joinToString("\n") { value -> "$name: $value" }
        }

    // ── pretty-printer ────────────────────────────────────────────────────────

    private fun prettyPrint(body: String, contentType: String?): Pair<String, String> {
        val ct = contentType?.lowercase() ?: ""
        return when {
            "json" in ct -> "json" to prettyJson(body)
            "xml"  in ct -> "xml"  to body   // raw; XML pretty-printing omitted for simplicity
            "yaml" in ct -> "yaml" to body   // YAML is already human-readable
            "html" in ct -> "html" to body
            ct.startsWith("text/") -> "txt"  to body
            else          -> "dat"  to body
        }
    }

    private fun prettyJson(body: String): String =
        runCatching {
            prettyJsonSerializer
                .parseToJsonElement(body)
                .let { prettyJsonSerializer.encodeToString(JsonObject.serializer(), it as JsonObject) }
        }.getOrElse {
            // Not a JSON object — try array or primitive, or just return raw
            runCatching {
                prettyJsonSerializer.parseToJsonElement(body)
                    .let { prettyJsonSerializer.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) }
            }.getOrDefault(body)
        }

    // ── HTTP reason phrases ───────────────────────────────────────────────────

    private companion object {
        val prettyJsonSerializer: Json = Json { prettyPrint = true }

        fun httpReason(code: Int): String = when (code) {
            100 -> "Continue"
            101 -> "Switching Protocols"
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            204 -> "No Content"
            206 -> "Partial Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            303 -> "See Other"
            304 -> "Not Modified"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            410 -> "Gone"
            415 -> "Unsupported Media Type"
            422 -> "Unprocessable Entity"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Unknown"
        }
    }
}
