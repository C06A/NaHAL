@file:OptIn(kotlin.js.ExperimentalJsExport::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

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
 * Bridges Kotlin [HaldishPlugin] calls to a `window.__haldishPlugin` JS object.
 *
 * Each hook converts Kotlin types to plain JS objects (no parsing needed by the
 * plugin), calls the corresponding JS function if it exists, and converts the
 * return value back.
 *
 * JS object shapes — see PLUGIN_CONTRACT.md for the authoritative reference.
 */
internal class JsPluginAdapter(private val jsPlugin: dynamic) : HaldishPlugin {

    override fun initialize(config: HaldishPluginConfig) {
        if (jsPlugin.initialize != null) {
            jsPlugin.initialize(config.platform, config.version)
        }
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        if (jsPlugin.preRequest == null) return request
        val jsReq    = requestToJs(request)
        val result   = jsPlugin.preRequest(jsReq)
        return if (result != null) jsToRequest(result, request) else request
    }

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
        if (jsPlugin.postResponse == null) return document
        val jsDoc    = documentToJs(document)
        val jsResp   = responseToJs(response)
        val result   = jsPlugin.postResponse(jsDoc, jsResp)
        return if (result != null) jsToDocument(result, document) else document
    }

    // ── Request → JS ─────────────────────────────────────────────────────────

    private fun requestToJs(req: HalHttpRequest): dynamic {
        val obj = js("{}")
        obj["url"]       = req.url
        obj["method"]    = req.method.value
        obj["headers"]   = stringMapToJs(req.headers)
        obj["cookies"]   = stringMapToJs(req.cookies)
        obj["body"]      = bodyToJs(req.body)
        obj["acceptHal"] = req.acceptHal
        return obj
    }

    private fun stringMapToJs(map: Map<String, String>): dynamic {
        val obj = js("{}")
        for ((k, v) in map) obj[k] = v
        return obj
    }

    private fun bodyToJs(body: HalRequestBody): dynamic {
        if (body is HalRequestBody.None) return null
        val obj = js("{}")
        when (body) {
            is HalRequestBody.Text       -> { obj["type"] = "text";       obj["content"] = body.content; obj["contentType"] = body.contentType }
            is HalRequestBody.Json       -> { obj["type"] = "json";       obj["content"] = body.content }
            is HalRequestBody.UrlEncoded -> { obj["type"] = "urlEncoded"; obj["params"]  = stringMapToJs(body.params) }
            is HalRequestBody.Binary     -> { obj["type"] = "binary";     obj["contentType"] = body.contentType }
            is HalRequestBody.FilePath   -> { obj["type"] = "filePath";   obj["path"] = body.path; obj["contentType"] = body.contentType }
            is HalRequestBody.Multipart  -> { obj["type"] = "multipart" }
            is HalRequestBody.None       -> {}
        }
        return obj
    }

    // ── JS → Request ─────────────────────────────────────────────────────────

    private fun jsToRequest(jsObj: dynamic, original: HalHttpRequest): HalHttpRequest {
        val url       = (jsObj["url"]       as? String) ?: original.url
        val method    = (jsObj["method"]    as? String)?.let { HttpMethod(it) } ?: original.method
        val acceptHal = (jsObj["acceptHal"] as? Boolean) ?: original.acceptHal
        val headers   = jsStringMapOrFallback(jsObj["headers"], original.headers)
        val cookies   = jsStringMapOrFallback(jsObj["cookies"], original.cookies)
        val body      = jsToBody(jsObj["body"], original.body)
        return original.copy(url = url, method = method, headers = headers,
                             cookies = cookies, body = body, acceptHal = acceptHal)
    }

    private fun jsStringMapOrFallback(jsObj: dynamic, fallback: Map<String, String>): Map<String, String> {
        if (jsObj == null || jsObj == undefined) return fallback
        return try {
            val keys = js("Object.keys(jsObj)") as Array<String>
            keys.associate { k -> k to ((jsObj[k] as? String) ?: "") }
        } catch (_: Throwable) { fallback }
    }

    private fun jsToBody(jsBody: dynamic, original: HalRequestBody): HalRequestBody {
        if (jsBody == null || jsBody == undefined) return original
        return when (jsBody["type"] as? String) {
            "text"       -> HalRequestBody.Text(
                content     = (jsBody["content"]     as? String) ?: "",
                contentType = (jsBody["contentType"] as? String) ?: "text/plain",
            )
            "json"       -> HalRequestBody.Json(content = (jsBody["content"] as? String) ?: "")
            "urlEncoded" -> HalRequestBody.UrlEncoded(jsStringMapOrFallback(jsBody["params"], emptyMap()))
            else         -> original
        }
    }

    // ── Document → JS ────────────────────────────────────────────────────────

    private fun documentToJs(doc: HalDocument): dynamic {
        val obj = js("{}")
        obj["links"]      = linksToJs(doc.links)
        obj["embedded"]   = embeddedToJs(doc.embedded)
        obj["properties"] = propertiesToJs(doc.properties)
        return obj
    }

    private fun linksToJs(links: Map<String, List<HalLink>>): dynamic {
        val obj = js("{}")
        for ((rel, list) in links) {
            val arr = js("[]")
            list.forEachIndexed { idx, link ->
                val l = js("{}")
                l["href"]        = link.href
                l["templated"]   = link.templated
                if (link.type        != null) l["type"]        = link.type
                if (link.name        != null) l["name"]        = link.name
                if (link.title       != null) l["title"]       = link.title
                if (link.hreflang    != null) l["hreflang"]    = link.hreflang
                if (link.profile     != null) l["profile"]     = link.profile
                if (link.deprecation != null) l["deprecation"] = link.deprecation
                arr[idx] = l
            }
            obj[rel] = arr
        }
        return obj
    }

    private fun embeddedToJs(embedded: Map<String, List<HalDocument>>): dynamic {
        val obj = js("{}")
        for ((rel, docs) in embedded) {
            val arr = js("[]")
            docs.forEachIndexed { i, d -> arr[i] = documentToJs(d) }
            obj[rel] = arr
        }
        return obj
    }

    private fun propertiesToJs(properties: Map<String, JsonElement>): dynamic {
        val obj = js("{}")
        for ((k, v) in properties) {
            obj[k] = js("JSON.parse(v.toString())")
        }
        return obj
    }

    private fun responseToJs(resp: HalHttpResponse): dynamic {
        val obj = js("{}")
        obj["statusCode"]  = resp.statusCode
        obj["headers"]     = multiStringMapToJs(resp.headers)
        obj["cookies"]     = stringMapToJs(resp.cookies)
        obj["body"]        = resp.body
        if (resp.contentType != null) obj["contentType"] = resp.contentType
        return obj
    }

    private fun multiStringMapToJs(map: Map<String, List<String>>): dynamic {
        val obj = js("{}")
        for ((k, vs) in map) {
            val arr = js("[]")
            vs.forEachIndexed { i, v -> arr[i] = v }
            obj[k] = arr
        }
        return obj
    }

    // ── JS → Document ────────────────────────────────────────────────────────

    private fun jsToDocument(jsDoc: dynamic, original: HalDocument): HalDocument {
        return try {
            val links      = if (jsDoc["links"]      != null) jsToLinks(jsDoc["links"])                  else original.links
            val embedded   = if (jsDoc["embedded"]   != null) jsToEmbedded(jsDoc["embedded"], original)  else original.embedded
            val properties = if (jsDoc["properties"] != null) jsToProperties(jsDoc["properties"])        else original.properties
            original.copy(links = links, embedded = embedded, properties = properties)
        } catch (_: Throwable) { original }
    }

    private fun jsToLinks(jsLinks: dynamic): Map<String, List<HalLink>> {
        val result = mutableMapOf<String, List<HalLink>>()
        val rels = js("Object.keys(jsLinks)") as Array<String>
        for (rel in rels) {
            val arr   = jsLinks[rel]
            val count = (arr.length as? Int) ?: 0
            result[rel] = (0 until count).mapNotNull { i ->
                val l = arr[i]
                val href = l["href"] as? String ?: return@mapNotNull null
                HalLink(
                    href        = href,
                    templated   = (l["templated"]   as? Boolean) ?: false,
                    type        = l["type"]        as? String,
                    name        = l["name"]        as? String,
                    title       = l["title"]       as? String,
                    hreflang    = l["hreflang"]    as? String,
                    profile     = l["profile"]     as? String,
                    deprecation = l["deprecation"] as? String,
                )
            }
        }
        return result
    }

    private fun jsToEmbedded(jsEmb: dynamic, original: HalDocument): Map<String, List<HalDocument>> {
        val result = mutableMapOf<String, List<HalDocument>>()
        val rels = js("Object.keys(jsEmb)") as Array<String>
        for (rel in rels) {
            val arr      = jsEmb[rel]
            val count    = (arr.length as? Int) ?: 0
            val origList = original.embedded[rel] ?: emptyList()
            result[rel]  = (0 until count).map { i ->
                val orig = origList.getOrNull(i) ?: HalDocument()
                jsToDocument(arr[i], orig)
            }
        }
        return result
    }

    private fun jsToProperties(jsProps: dynamic): Map<String, JsonElement> {
        val json = Json { ignoreUnknownKeys = true }
        val result = mutableMapOf<String, JsonElement>()
        val keys = js("Object.keys(jsProps)") as Array<String>
        for (k in keys) {
            val jsonStr = js("JSON.stringify(jsProps[k])") as? String ?: continue
            try { result[k] = json.parseToJsonElement(jsonStr) } catch (_: Throwable) {}
        }
        return result
    }
}
