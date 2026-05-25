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
 * Bridges C function-pointer callbacks (registered via `haldish_plugin_register()`)
 * to the [HaldishPlugin] interface.
 *
 * This is the in-process registration path: C callers pass function pointers directly
 * rather than packaging their plugin as a separate dynamic library.
 *
 * The C callback signatures match those of the dynamic-library plugin exactly —
 * see `NativeDylibPluginAdapter` and `PLUGIN_CONTRACT.md` for the full specification.
 *
 * Must be registered (via `haldish_plugin_register()`) **before** the first HTTP call.
 */
internal class CFunctionPluginAdapter(
    private val initFn:    CPointer<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>>?,
    private val preReqFn:  CPointer<CFunction<(
        CPointer<ByteVar>?,                  // url
        CPointer<ByteVar>?,                  // method
        Int,                                  // header_count
        CPointer<CPointerVar<ByteVar>>?,     // header_keys
        CPointer<CPointerVar<ByteVar>>?,     // header_vals
        CPointer<ByteVar>?,                  // body
        CPointer<ByteVar>?,                  // content_type
        Int,                                  // accept_hal
    ) -> CPointer<ByteVar>?>>?,
    private val postResFn: CPointer<CFunction<(
        Int,                                  // link_count
        CPointer<CPointerVar<ByteVar>>?,     // link_rels
        CPointer<CPointerVar<ByteVar>>?,     // link_hrefs
        CPointer<IntVar>?,                   // link_templated
        Int,                                  // prop_count
        CPointer<CPointerVar<ByteVar>>?,     // prop_keys
        CPointer<CPointerVar<ByteVar>>?,     // prop_json_vals
        Int,                                  // status_code
        CPointer<ByteVar>?,                  // resp_body
        CPointer<ByteVar>?,                  // resp_content_type
    ) -> CPointer<ByteVar>?>>?,
) : HaldishPlugin {

    private val json = Json { ignoreUnknownKeys = true }

    override fun initialize(config: HaldishPluginConfig) {
        val fn = initFn ?: return
        memScoped {
            fn(config.platform.cstr.ptr, config.version.cstr.ptr)
        }
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        val fn = preReqFn ?: return request
        val resultJson: String? = memScoped {
            val headers     = request.headers.entries.toList()
            val headerCount = headers.size
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
        val fn = postResFn ?: return document
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

    // ── JSON diff parsers (identical to NativeDylibPluginAdapter) ─────────────

    private fun applyPreRequestDiff(jsonStr: String, original: HalHttpRequest): HalHttpRequest {
        return try {
            val obj       = json.parseToJsonElement(jsonStr).jsonObject
            val url       = obj["url"]?.jsonPrimitive?.contentOrNull ?: original.url
            val method    = obj["method"]?.jsonPrimitive?.contentOrNull?.let { HttpMethod(it) } ?: original.method
            val acceptHal = obj["acceptHal"]?.jsonPrimitive?.booleanOrNull ?: original.acceptHal
            val headers   = obj["headers"]?.jsonObject
                ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                ?: original.headers
            val bodyStr = obj["body"]?.jsonPrimitive?.contentOrNull
            val ct      = obj["contentType"]?.jsonPrimitive?.contentOrNull
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
            val properties = obj["properties"]?.jsonObject?.mapValues { (_, v) -> v } ?: original.properties
            original.copy(links = links, properties = properties)
        } catch (_: Throwable) { original }
    }

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
