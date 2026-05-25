@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.http.HttpMethod
import kotlinx.cinterop.*
import kotlinx.serialization.json.*

/**
 * Adapts a dynamically-loaded native plugin library (`.so` / `.dll` / `.dylib`)
 * to [HaldishPlugin].
 *
 * ## Plugin library contract
 *
 * The plugin library **may** export any (or all) of these three C functions;
 * missing symbols are treated as no-ops:
 *
 * ```c
 * // Called once at startup.
 * void haldish_plugin_init(const char* platform, const char* version);
 *
 * // Called before each request.  Return NULL to keep the request unchanged,
 * // or return a JSON string describing the fields to override.
 * // The returned pointer must remain valid until the next call to this function.
 * const char* haldish_plugin_pre_request(
 *     const char* url,
 *     const char* method,
 *     int         header_count,
 *     const char** header_keys,   // header_count entries
 *     const char** header_vals,   // header_count entries
 *     const char* body,           // NULL if no body
 *     const char* content_type,   // NULL if unknown
 *     int         accept_hal      // 1 = true, 0 = false
 * );
 *
 * // Called after HAL parsing.  Return NULL to keep the document unchanged,
 * // or return a JSON string describing the fields to override.
 * const char* haldish_plugin_post_response(
 *     int         link_count,
 *     const char** link_rels,
 *     const char** link_hrefs,
 *     const int*  link_templated, // 1 or 0 per link
 *     int         prop_count,
 *     const char** prop_keys,
 *     const char** prop_json_vals,// JSON-encoded property values
 *     int         status_code,
 *     const char* resp_body,
 *     const char* resp_content_type // NULL if absent
 * );
 * ```
 *
 * ## Return-value JSON format for `haldish_plugin_pre_request`
 *
 * ```json
 * {
 *   "url":        "https://...",    // omit to keep original
 *   "method":     "POST",           // omit to keep original
 *   "headers":    { "key": "val" }, // replaces ALL headers if present
 *   "body":       "...",            // omit to keep original
 *   "contentType":"application/json",
 *   "acceptHal":  true
 * }
 * ```
 *
 * ## Return-value JSON format for `haldish_plugin_post_response`
 *
 * ```json
 * {
 *   "links": {
 *     "self": [{ "href": "https://...", "templated": false }]
 *   },
 *   "properties": { "key": "value" }
 * }
 * ```
 *
 * See `PLUGIN_CONTRACT.md` for the complete reference.
 */
internal class NativeDylibPluginAdapter private constructor(
    private val handle: COpaquePointer,
    private val initSym:    COpaquePointer?,
    private val preReqSym:  COpaquePointer?,
    private val postResSym: COpaquePointer?,
) : HaldishPlugin {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Attempt to resolve plugin symbols from an open [handle].
         * Returns `null` if none of the three expected symbols is present.
         */
        fun from(handle: COpaquePointer): NativeDylibPluginAdapter? {
            val init    = platformDlsym(handle, "haldish_plugin_init")
            val preReq  = platformDlsym(handle, "haldish_plugin_pre_request")
            val postRes = platformDlsym(handle, "haldish_plugin_post_response")
            if (init == null && preReq == null && postRes == null) return null
            return NativeDylibPluginAdapter(handle, init, preReq, postRes)
        }
    }

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    override fun initialize(config: HaldishPluginConfig) {
        val fn = initSym?.reinterpret<CFunction<(
            CPointer<ByteVar>?,
            CPointer<ByteVar>?,
        ) -> Unit>>() ?: return
        memScoped {
            fn(config.platform.cstr.ptr, config.version.cstr.ptr)
        }
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        val fn = preReqSym?.reinterpret<CFunction<(
            CPointer<ByteVar>?,  // url
            CPointer<ByteVar>?,  // method
            Int,                  // header_count
            CPointer<CPointerVar<ByteVar>>?, // header_keys
            CPointer<CPointerVar<ByteVar>>?, // header_vals
            CPointer<ByteVar>?,  // body
            CPointer<ByteVar>?,  // content_type
            Int,                  // accept_hal
        ) -> CPointer<ByteVar>?>>() ?: return request

        val resultJson: String? = memScoped {
            val headers      = request.headers.entries.toList()
            val headerCount  = headers.size
            val keys = allocArray<CPointerVar<ByteVar>>(maxOf(headerCount, 1)).also { arr ->
                headers.forEachIndexed { i, (k, _) -> arr[i] = k.cstr.ptr }
            }
            val vals = allocArray<CPointerVar<ByteVar>>(maxOf(headerCount, 1)).also { arr ->
                headers.forEachIndexed { i, (_, v) -> arr[i] = v.cstr.ptr }
            }
            val bodyStr = request.body.toStringOrNull()
            val ct      = request.body.contentTypeOrNull()
            fn(
                request.url.cstr.ptr,
                request.method.value.cstr.ptr,
                headerCount,
                if (headerCount > 0) keys else null,
                if (headerCount > 0) vals else null,
                bodyStr?.cstr?.ptr,
                ct?.cstr?.ptr,
                if (request.acceptHal) 1 else 0,
            )?.toKString()
        }

        return resultJson?.let { applyPreRequestDiff(it, request) } ?: request
    }

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
        val fn = postResSym?.reinterpret<CFunction<(
            Int,                  // link_count
            CPointer<CPointerVar<ByteVar>>?, // link_rels
            CPointer<CPointerVar<ByteVar>>?, // link_hrefs
            CPointer<IntVar>?,    // link_templated
            Int,                  // prop_count
            CPointer<CPointerVar<ByteVar>>?, // prop_keys
            CPointer<CPointerVar<ByteVar>>?, // prop_json_vals
            Int,                  // status_code
            CPointer<ByteVar>?,  // resp_body
            CPointer<ByteVar>?,  // resp_content_type
        ) -> CPointer<ByteVar>?>>() ?: return document

        val allLinks = document.links.flatMap { (rel, list) -> list.map { rel to it } }
        val props    = document.properties.entries.toList()

        val resultJson: String? = memScoped {
            val linkCount = allLinks.size
            val rels = allocArray<CPointerVar<ByteVar>>(maxOf(linkCount, 1)).also { arr ->
                allLinks.forEachIndexed { i, (rel, _) -> arr[i] = rel.cstr.ptr }
            }
            val hrefs = allocArray<CPointerVar<ByteVar>>(maxOf(linkCount, 1)).also { arr ->
                allLinks.forEachIndexed { i, (_, l) -> arr[i] = l.href.cstr.ptr }
            }
            val templated = allocArray<IntVar>(maxOf(linkCount, 1)).also { arr ->
                allLinks.forEachIndexed { i, (_, l) -> arr[i] = if (l.templated) 1 else 0 }
            }

            val propCount = props.size
            val pKeys = allocArray<CPointerVar<ByteVar>>(maxOf(propCount, 1)).also { arr ->
                props.forEachIndexed { i, (k, _) -> arr[i] = k.cstr.ptr }
            }
            val pVals = allocArray<CPointerVar<ByteVar>>(maxOf(propCount, 1)).also { arr ->
                props.forEachIndexed { i, (_, v) -> arr[i] = v.toString().cstr.ptr }
            }

            fn(
                linkCount,
                if (linkCount > 0) rels else null,
                if (linkCount > 0) hrefs else null,
                if (linkCount > 0) templated else null,
                propCount,
                if (propCount > 0) pKeys else null,
                if (propCount > 0) pVals else null,
                response.statusCode,
                response.body.cstr.ptr,
                response.contentType?.cstr?.ptr,
            )?.toKString()
        }

        return resultJson?.let { applyPostResponseDiff(it, document) } ?: document
    }

    // ── JSON diff parsers ─────────────────────────────────────────────────────

    private fun applyPreRequestDiff(jsonStr: String, original: HalHttpRequest): HalHttpRequest {
        return try {
            val obj      = json.parseToJsonElement(jsonStr).jsonObject
            val url      = obj["url"]?.jsonPrimitive?.contentOrNull     ?: original.url
            val method   = obj["method"]?.jsonPrimitive?.contentOrNull?.let { HttpMethod(it) } ?: original.method
            val acceptHal = obj["acceptHal"]?.jsonPrimitive?.booleanOrNull ?: original.acceptHal
            val headers  = obj["headers"]?.jsonObject
                ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                ?: original.headers
            val bodyStr  = obj["body"]?.jsonPrimitive?.contentOrNull
            val ct       = obj["contentType"]?.jsonPrimitive?.contentOrNull
            val body = when {
                bodyStr == null -> original.body
                ct?.contains("json") == true -> HalRequestBody.Json(bodyStr)
                else -> HalRequestBody.Text(bodyStr, ct ?: "text/plain")
            }
            original.copy(url = url, method = method, headers = headers, body = body, acceptHal = acceptHal)
        } catch (_: Throwable) { original }
    }

    private fun applyPostResponseDiff(jsonStr: String, original: HalDocument): HalDocument {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject

            val links = obj["links"]?.jsonObject?.mapValues { (_, arr) ->
                arr.jsonArray.map { linkEl ->
                    val l = linkEl.jsonObject
                    HalLink(
                        href      = l["href"]?.jsonPrimitive?.content ?: "",
                        templated = l["templated"]?.jsonPrimitive?.booleanOrNull ?: false,
                        type      = l["type"]?.jsonPrimitive?.contentOrNull,
                        name      = l["name"]?.jsonPrimitive?.contentOrNull,
                        title     = l["title"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            } ?: original.links

            val properties = obj["properties"]?.jsonObject
                ?.mapValues { (_, v) -> v }
                ?: original.properties

            original.copy(links = links, properties = properties)
        } catch (_: Throwable) { original }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun HalRequestBody.toStringOrNull(): String? = when (this) {
        is HalRequestBody.Text -> content
        is HalRequestBody.Json -> content
        else -> null
    }

    private fun HalRequestBody.contentTypeOrNull(): String? = when (this) {
        is HalRequestBody.Text   -> contentType
        is HalRequestBody.Json   -> "application/json"
        is HalRequestBody.Binary -> contentType
        else -> null
    }
}

/**
 * Platform-specific symbol resolution.
 * `actual` implementations are in `appleMain`, `linuxMain`, and `mingwMain`.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal expect fun platformDlsym(handle: COpaquePointer, symbol: String): CPointer<*>?
