@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.haldish.parser.HalParser
import io.ktor.http.HttpMethod
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Author-side helper for a native plugin shared library (`libhaldish_plugin.{so,dylib,dll}`).
 *
 * A plugin module exports thin `@CName` C functions — `haldish_plugin_init`,
 * `haldish_plugin_pre_link`, `haldish_plugin_pre_request`, `haldish_plugin_post_response` — that
 * delegate to the methods here, passing their [HaldishPlugin]. These perform the inverse of
 * [NativeDylibPluginAdapter]: they rebuild the request / link / document from the C arguments,
 * invoke the plugin, and return a JSON diff as a C string — or `null` when the plugin made no
 * change (the adapter then keeps the original unchanged).
 *
 * Each hook owns a single heap C string that is freed on that hook's next call, satisfying the
 * ABI rule that a returned pointer stays valid only until the next call to the same function.
 * HALDiSh calls plugins serially, so per-hook single ownership is safe.
 */
object NativePluginBridge {

    private val json = Json { ignoreUnknownKeys = true }

    private var preLinkBuf: CPointer<ByteVar>? = null
    private var preReqBuf:  CPointer<ByteVar>? = null
    private var postResBuf: CPointer<ByteVar>? = null

    fun init(plugin: HaldishPlugin, platform: CPointer<ByteVar>?, version: CPointer<ByteVar>?) {
        plugin.initialize(
            HaldishPluginConfig(
                platform = platform?.toKString() ?: "native",
                version  = version?.toKString() ?: "",
            )
        )
    }

    fun preLink(
        plugin: HaldishPlugin,
        rel: CPointer<ByteVar>?,
        href: CPointer<ByteVar>?,
        templated: Int,
        type: CPointer<ByteVar>?,
        name: CPointer<ByteVar>?,
        title: CPointer<ByteVar>?,
        rootBody: CPointer<ByteVar>?,
        pathJson: CPointer<ByteVar>?,
    ): CPointer<ByteVar>? {
        val link = HalLink(
            href      = href?.toKString() ?: "",
            templated = templated != 0,
            type      = type?.toKString(),
            name      = name?.toKString(),
            title     = title?.toKString(),
        )
        val root = rootBody?.toKString()?.let { body ->
            try { HalParser.parse(body, null) } catch (_: Throwable) { null }
        } ?: HalDocument()
        val path = pathJson?.toKString()?.let { ResourcePath.fromJson(it) }
            ?: ResourcePath.link(rel?.toKString() ?: ResourcePath.SELF_REL)

        val result = plugin.preLink(link, path, root)
        if (result == link) return null

        val diff = buildJsonObject {
            put("href", result.href)
            put("templated", result.templated)
            result.type?.let { put("type", it) }
            result.name?.let { put("name", it) }
            result.title?.let { put("title", it) }
        }
        preLinkBuf = retain(preLinkBuf, diff.toString())
        return preLinkBuf
    }

    fun preRequest(
        plugin: HaldishPlugin,
        url: CPointer<ByteVar>?,
        method: CPointer<ByteVar>?,
        headerCount: Int,
        headerKeys: CPointer<CPointerVar<ByteVar>>?,
        headerVals: CPointer<CPointerVar<ByteVar>>?,
        body: CPointer<ByteVar>?,
        contentType: CPointer<ByteVar>?,
        acceptHal: Int,
    ): CPointer<ByteVar>? {
        val headers = LinkedHashMap<String, String>()
        for (i in 0 until headerCount) {
            val k = headerKeys?.get(i)?.toKString() ?: continue
            headers[k] = headerVals?.get(i)?.toKString() ?: ""
        }
        val bodyStr = body?.toKString()
        val ctStr = contentType?.toKString()
        val reqBody: HalRequestBody = when {
            bodyStr == null -> HalRequestBody.None
            ctStr?.contains("json") == true -> HalRequestBody.Json(bodyStr)
            else -> HalRequestBody.Text(bodyStr, ctStr ?: "text/plain")
        }
        val request = HalHttpRequest(
            url       = url?.toKString() ?: "",
            method    = HttpMethod.parse(method?.toKString() ?: "GET"),
            headers   = headers,
            body      = reqBody,
            acceptHal = acceptHal != 0,
        )
        val result = plugin.preRequest(request)
        if (result == request) return null

        val diff = buildJsonObject {
            put("url", result.url)
            put("method", result.method.value)
            put("acceptHal", result.acceptHal)
            putJsonObject("headers") { result.headers.forEach { (k, v) -> put(k, v) } }
            bodyOf(result.body)?.let { (content, ct) ->
                put("body", content)
                put("contentType", ct)
            }
        }
        preReqBuf = retain(preReqBuf, diff.toString())
        return preReqBuf
    }

    fun postResponse(
        plugin: HaldishPlugin,
        linkCount: Int,
        linkRels: CPointer<CPointerVar<ByteVar>>?,
        linkHrefs: CPointer<CPointerVar<ByteVar>>?,
        linkTemplated: CPointer<IntVar>?,
        propCount: Int,
        propKeys: CPointer<CPointerVar<ByteVar>>?,
        propJsonVals: CPointer<CPointerVar<ByteVar>>?,
        statusCode: Int,
        respBody: CPointer<ByteVar>?,
        respContentType: CPointer<ByteVar>?,
    ): CPointer<ByteVar>? {
        val links = LinkedHashMap<String, MutableList<HalLink>>()
        for (i in 0 until linkCount) {
            val rel = linkRels?.get(i)?.toKString() ?: continue
            val href = linkHrefs?.get(i)?.toKString() ?: ""
            val t = linkTemplated?.get(i) ?: 0
            links.getOrPut(rel) { mutableListOf() }.add(HalLink(href = href, templated = t != 0))
        }
        val props = LinkedHashMap<String, JsonElement>()
        for (i in 0 until propCount) {
            val k = propKeys?.get(i)?.toKString() ?: continue
            val raw = propJsonVals?.get(i)?.toKString() ?: "null"
            props[k] = try { json.parseToJsonElement(raw) } catch (_: Throwable) { JsonPrimitive(raw) }
        }
        val bodyStr = respBody?.toKString() ?: ""
        val document = HalDocument(links = links, properties = props, rawBody = bodyStr)
        val response = HalHttpResponse(
            statusCode  = statusCode,
            headers     = emptyMap(),
            cookies     = emptyMap(),
            body        = bodyStr,
            contentType = respContentType?.toKString(),
        )
        val result = plugin.postResponse(document, response)
        if (result == document) return null

        val diff = buildJsonObject {
            putJsonObject("links") {
                result.links.forEach { (rel, list) ->
                    putJsonArray(rel) {
                        list.forEach { add(buildJsonObject { put("href", it.href); put("templated", it.templated) }) }
                    }
                }
            }
            putJsonObject("properties") { result.properties.forEach { (k, v) -> put(k, v) } }
        }
        postResBuf = retain(postResBuf, diff.toString())
        return postResBuf
    }

    /** The (content, contentType) of a request body that can be sent as a string, or null. */
    private fun bodyOf(body: HalRequestBody): Pair<String, String>? = when (body) {
        is HalRequestBody.Text -> body.content to body.contentType
        is HalRequestBody.Json -> body.content to "application/json"
        else -> null
    }

    /** Frees [old], then returns a fresh heap-allocated NUL-terminated C copy of [s]. */
    private fun retain(old: CPointer<ByteVar>?, s: String): CPointer<ByteVar> {
        old?.let { nativeHeap.free(it) }
        val bytes = s.encodeToByteArray()
        val ptr = nativeHeap.allocArray<ByteVar>(bytes.size + 1)
        for (i in bytes.indices) ptr[i] = bytes[i]
        ptr[bytes.size] = 0
        return ptr
    }
}
