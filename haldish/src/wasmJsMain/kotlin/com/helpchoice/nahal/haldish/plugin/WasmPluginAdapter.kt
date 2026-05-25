package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Bridges Kotlin [HaldishPlugin] calls to a `window.__haldishPlugin` JS object
 * on the Kotlin/Wasm target.
 *
 * Uses the same `window.__haldishPlugin` contract as the JS target.
 * See `PLUGIN_CONTRACT.md` for the full specification.
 *
 * All `js()` calls are isolated to single-expression top-level functions below
 * the class (Kotlin/Wasm requirement).
 */
internal class WasmPluginAdapter(private val jsPlugin: JsAny) : HaldishPlugin {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    override fun initialize(config: HaldishPluginConfig) {
        wasmCallInit(jsPlugin, config.platform, config.version)
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        val jsReq  = buildRequestJsAny(request)
        val result = wasmCallPreRequest(jsPlugin, jsReq) ?: return request
        return jsAnyToRequest(result, request)
    }

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
        val jsDoc  = buildDocumentJsAny(document)
        val jsResp = buildResponseJsAny(response)
        val result = wasmCallPostResponse(jsPlugin, jsDoc, jsResp) ?: return document
        return jsAnyToDocument(result, document)
    }

    // ── Build request JsAny ───────────────────────────────────────────────────

    private fun buildRequestJsAny(req: HalHttpRequest): JsAny {
        val obj = wasmCreateObject()
        wasmSetString(obj, "url",    req.url)
        wasmSetString(obj, "method", req.method.value)
        wasmSetAny(obj, "headers",   buildStringMapJsAny(req.headers))
        wasmSetAny(obj, "cookies",   buildStringMapJsAny(req.cookies))
        wasmSetBoolean(obj, "acceptHal", req.acceptHal)
        val bodyAny = buildBodyJsAny(req.body)
        if (bodyAny != null) wasmSetAny(obj, "body", bodyAny)
        return obj
    }

    private fun buildStringMapJsAny(map: Map<String, String>): JsAny {
        val obj = wasmCreateObject()
        for ((k, v) in map) wasmSetString(obj, k, v)
        return obj
    }

    private fun buildBodyJsAny(body: HalRequestBody): JsAny? {
        if (body is HalRequestBody.None) return null
        val obj = wasmCreateObject()
        when (body) {
            is HalRequestBody.Text       -> { wasmSetString(obj, "type", "text"); wasmSetString(obj, "content", body.content); wasmSetString(obj, "contentType", body.contentType) }
            is HalRequestBody.Json       -> { wasmSetString(obj, "type", "json"); wasmSetString(obj, "content", body.content) }
            is HalRequestBody.UrlEncoded -> { wasmSetString(obj, "type", "urlEncoded"); wasmSetAny(obj, "params", buildStringMapJsAny(body.params)) }
            is HalRequestBody.Binary     -> { wasmSetString(obj, "type", "binary"); wasmSetString(obj, "contentType", body.contentType) }
            is HalRequestBody.FilePath   -> { wasmSetString(obj, "type", "filePath"); wasmSetString(obj, "path", body.path); wasmSetString(obj, "contentType", body.contentType) }
            is HalRequestBody.Multipart  -> { wasmSetString(obj, "type", "multipart") }
            is HalRequestBody.None       -> {}
        }
        return obj
    }

    // ── Build document / response JsAny ──────────────────────────────────────

    private fun buildDocumentJsAny(doc: HalDocument): JsAny {
        val obj = wasmCreateObject()
        wasmSetAny(obj, "links",      buildLinksJsAny(doc.links))
        wasmSetAny(obj, "embedded",   buildEmbeddedJsAny(doc.embedded))
        wasmSetAny(obj, "properties", buildPropertiesJsAny(doc.properties))
        return obj
    }

    private fun buildLinksJsAny(links: Map<String, List<HalLink>>): JsAny {
        val obj = wasmCreateObject()
        for ((rel, list) in links) {
            val arr = wasmCreateArray()
            list.forEachIndexed { i, link ->
                val l = wasmCreateObject()
                wasmSetString(l, "href", link.href)
                wasmSetBoolean(l, "templated", link.templated)
                link.type?.let        { wasmSetString(l, "type",        it) }
                link.name?.let        { wasmSetString(l, "name",        it) }
                link.title?.let       { wasmSetString(l, "title",       it) }
                link.hreflang?.let    { wasmSetString(l, "hreflang",    it) }
                link.profile?.let     { wasmSetString(l, "profile",     it) }
                link.deprecation?.let { wasmSetString(l, "deprecation", it) }
                wasmArraySetAny(arr, i, l)
            }
            wasmSetAny(obj, rel, arr)
        }
        return obj
    }

    private fun buildEmbeddedJsAny(embedded: Map<String, List<HalDocument>>): JsAny {
        val obj = wasmCreateObject()
        for ((rel, docs) in embedded) {
            val arr = wasmCreateArray()
            docs.forEachIndexed { i, d -> wasmArraySetAny(arr, i, buildDocumentJsAny(d)) }
            wasmSetAny(obj, rel, arr)
        }
        return obj
    }

    private fun buildPropertiesJsAny(properties: Map<String, JsonElement>): JsAny {
        val obj = wasmCreateObject()
        for ((k, v) in properties) {
            val parsed = wasmJsonParse(v.toString())
            if (parsed != null) wasmSetAny(obj, k, parsed)
        }
        return obj
    }

    private fun buildResponseJsAny(resp: HalHttpResponse): JsAny {
        val obj = wasmCreateObject()
        wasmSetInt(obj, "statusCode", resp.statusCode)
        wasmSetAny(obj, "headers",    buildMultiStringMapJsAny(resp.headers))
        wasmSetAny(obj, "cookies",    buildStringMapJsAny(resp.cookies))
        wasmSetString(obj, "body",    resp.body)
        resp.contentType?.let { wasmSetString(obj, "contentType", it) }
        return obj
    }

    private fun buildMultiStringMapJsAny(map: Map<String, List<String>>): JsAny {
        val obj = wasmCreateObject()
        for ((k, vs) in map) {
            val arr = wasmCreateArray()
            vs.forEachIndexed { i, v -> wasmArraySetString(arr, i, v) }
            wasmSetAny(obj, k, arr)
        }
        return obj
    }

    // ── JsAny → Kotlin types ──────────────────────────────────────────────────

    private fun jsAnyToRequest(jsObj: JsAny, original: HalHttpRequest): HalHttpRequest {
        return try {
            val url       = wasmGetString(jsObj, "url")       ?: original.url
            val method    = wasmGetString(jsObj, "method")?.let { HttpMethod(it) } ?: original.method
            val acceptHal = wasmGetBoolean(jsObj, "acceptHal") ?: original.acceptHal
            val headersAny = wasmGetAny(jsObj, "headers")
            val cookiesAny = wasmGetAny(jsObj, "cookies")
            val headers    = if (headersAny != null) jsAnyToStringMap(headersAny) ?: original.headers else original.headers
            val cookies    = if (cookiesAny != null) jsAnyToStringMap(cookiesAny) ?: original.cookies else original.cookies
            val bodyAny    = wasmGetAny(jsObj, "body")
            val body       = if (bodyAny != null) jsAnyToBody(bodyAny, original.body) else original.body
            original.copy(url = url, method = method, headers = headers,
                          cookies = cookies, body = body, acceptHal = acceptHal)
        } catch (_: Throwable) { original }
    }

    private fun jsAnyToStringMap(jsObj: JsAny): Map<String, String>? {
        return try {
            val keys = wasmObjectKeys(jsObj) ?: return null
            val count = wasmArrayLength(keys)
            (0 until count).associate { i ->
                val k = wasmArrayGetString(keys, i) ?: ""
                val v = wasmGetString(jsObj, k) ?: ""
                k to v
            }
        } catch (_: Throwable) { null }
    }

    private fun jsAnyToBody(jsBody: JsAny, original: HalRequestBody): HalRequestBody {
        return when (wasmGetString(jsBody, "type")) {
            "text"       -> HalRequestBody.Text(
                content     = wasmGetString(jsBody, "content")     ?: "",
                contentType = wasmGetString(jsBody, "contentType") ?: "text/plain",
            )
            "json"       -> HalRequestBody.Json(content = wasmGetString(jsBody, "content") ?: "")
            "urlEncoded" -> {
                val paramsAny = wasmGetAny(jsBody, "params")
                val params = if (paramsAny != null) jsAnyToStringMap(paramsAny) ?: emptyMap() else emptyMap()
                HalRequestBody.UrlEncoded(params)
            }
            else -> original
        }
    }

    private fun jsAnyToDocument(jsObj: JsAny, original: HalDocument): HalDocument {
        return try {
            val linksAny    = wasmGetAny(jsObj, "links")
            val embeddedAny = wasmGetAny(jsObj, "embedded")
            val propsAny    = wasmGetAny(jsObj, "properties")
            val links      = if (linksAny    != null) jsAnyToLinks(linksAny)             else original.links
            val embedded   = if (embeddedAny != null) jsAnyToEmbedded(embeddedAny, original) else original.embedded
            val properties = if (propsAny    != null) jsAnyToProperties(propsAny)        else original.properties
            original.copy(links = links, embedded = embedded, properties = properties)
        } catch (_: Throwable) { original }
    }

    private fun jsAnyToLinks(jsLinks: JsAny): Map<String, List<HalLink>> {
        val result = mutableMapOf<String, List<HalLink>>()
        val keys = wasmObjectKeys(jsLinks) ?: return result
        val keyCount = wasmArrayLength(keys)
        for (ki in 0 until keyCount) {
            val rel = wasmArrayGetString(keys, ki) ?: continue
            val arr = wasmGetAny(jsLinks, rel) ?: continue
            val count = wasmArrayLength(arr)
            result[rel] = (0 until count).mapNotNull { i ->
                val l = wasmArrayGetAny(arr, i) ?: return@mapNotNull null
                val href = wasmGetString(l, "href") ?: return@mapNotNull null
                HalLink(
                    href        = href,
                    templated   = wasmGetBoolean(l, "templated") ?: false,
                    type        = wasmGetString(l, "type"),
                    name        = wasmGetString(l, "name"),
                    title       = wasmGetString(l, "title"),
                    hreflang    = wasmGetString(l, "hreflang"),
                    profile     = wasmGetString(l, "profile"),
                    deprecation = wasmGetString(l, "deprecation"),
                )
            }
        }
        return result
    }

    private fun jsAnyToEmbedded(jsEmb: JsAny, original: HalDocument): Map<String, List<HalDocument>> {
        val result = mutableMapOf<String, List<HalDocument>>()
        val keys = wasmObjectKeys(jsEmb) ?: return result
        val keyCount = wasmArrayLength(keys)
        for (ki in 0 until keyCount) {
            val rel      = wasmArrayGetString(keys, ki) ?: continue
            val arr      = wasmGetAny(jsEmb, rel) ?: continue
            val count    = wasmArrayLength(arr)
            val origList = original.embedded[rel] ?: emptyList()
            result[rel]  = (0 until count).map { i ->
                val child    = wasmArrayGetAny(arr, i)
                val origChild = origList.getOrNull(i) ?: HalDocument()
                if (child != null) jsAnyToDocument(child, origChild) else origChild
            }
        }
        return result
    }

    private fun jsAnyToProperties(jsProps: JsAny): Map<String, JsonElement> {
        val result = mutableMapOf<String, JsonElement>()
        val keys   = wasmObjectKeys(jsProps) ?: return result
        val count  = wasmArrayLength(keys)
        for (i in 0 until count) {
            val k      = wasmArrayGetString(keys, i) ?: continue
            val valAny = wasmGetAny(jsProps, k) ?: continue
            val str    = wasmJsonStringify(valAny) ?: continue
            try { result[k] = json.parseToJsonElement(str) } catch (_: Throwable) {}
        }
        return result
    }
}

// ── Single-expression top-level helpers (Kotlin/Wasm requires js() here) ──────
// Each function body is a single `js()` expression — Kotlin/Wasm requirement.

private fun wasmCreateObject(): JsAny = js("({})")
private fun wasmCreateArray():  JsAny = js("([])")

private fun wasmSetString(obj: JsAny, key: String, value: String): Unit  = js("obj[key] = value")
private fun wasmSetBoolean(obj: JsAny, key: String, value: Boolean): Unit = js("obj[key] = value")
private fun wasmSetInt(obj: JsAny, key: String, value: Int): Unit         = js("obj[key] = value")
private fun wasmSetAny(obj: JsAny, key: String, value: JsAny): Unit       = js("obj[key] = value")

private fun wasmArraySetAny(arr: JsAny, i: Int, value: JsAny): Unit    = js("arr[i] = value")
private fun wasmArraySetString(arr: JsAny, i: Int, value: String): Unit = js("arr[i] = value")

private fun wasmGetString(obj: JsAny, key: String): String? =
    js("(typeof obj[key] === 'string') ? obj[key] : null")

private fun wasmGetBoolean(obj: JsAny, key: String): Boolean? =
    js("(typeof obj[key] === 'boolean') ? obj[key] : null")

private fun wasmGetAny(obj: JsAny, key: String): JsAny? =
    js("(obj[key] != null && obj[key] !== undefined) ? obj[key] : null")

private fun wasmArrayGetAny(arr: JsAny, i: Int): JsAny? =
    js("(arr[i] != null && arr[i] !== undefined) ? arr[i] : null")

private fun wasmArrayGetString(arr: JsAny, i: Int): String? =
    js("(typeof arr[i] === 'string') ? arr[i] : null")

private fun wasmArrayLength(arr: JsAny): Int = js("arr.length | 0")

private fun wasmObjectKeys(obj: JsAny): JsAny? =
    js("(obj != null && typeof obj === 'object') ? Object.keys(obj) : null")

private fun wasmJsonParse(str: String): JsAny? =
    js("(function(s){try{return JSON.parse(s)}catch(e){return null}})(str)")

private fun wasmJsonStringify(value: JsAny): String? =
    js("(function(v){try{return JSON.stringify(v)}catch(e){return null}})(value)")

// ── Plugin call helpers (single-expression, top-level) ────────────────────────

private fun wasmCallInit(plugin: JsAny, platform: String, version: String): Unit =
    js("(typeof plugin.initialize === 'function') && plugin.initialize(platform, version)")

private fun wasmCallPreRequest(plugin: JsAny, req: JsAny): JsAny? =
    js("(typeof plugin.preRequest === 'function') ? plugin.preRequest(req) : null")

private fun wasmCallPostResponse(plugin: JsAny, doc: JsAny, resp: JsAny): JsAny? =
    js("(typeof plugin.postResponse === 'function') ? plugin.postResponse(doc, resp) : null")
