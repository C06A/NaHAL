@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.helpchoice.nahal.haldish

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

/** HTTP response wrapper visible to JavaScript callers. */
@JsExport
class JsResponse(val statusCode: Int, val body: String)

/**
 * Immutable-ish map of HTTP request headers passed to [JsHalClient] methods.
 *
 * Usage (JS):
 *   const h = new JsHeaders().set('Accept', 'application/pdf').set('Authorization', 'Bearer …')
 *   await client.get(url, h)
 */
@JsExport
class JsHeaders {
    private val map = mutableMapOf<String, String>()
    fun set(key: String, value: String): JsHeaders { map[key] = value; return this }
    internal fun toMap(): Map<String, String> = map.toMap()
}

/**
 * One part in a multipart/form-data upload passed to [JsHalClient.postMultipart].
 *
 * [bytes] is an Int8Array in JavaScript (Kotlin's ByteArray representation).
 * Convert a Node.js Buffer with:
 *   new Int8Array(buf.buffer, buf.byteOffset, buf.byteLength)
 */
@JsExport
class JsMultipartPart(
    val name: String,
    val bytes: ByteArray,
    val contentType: String = "application/octet-stream",
    val fileName: String? = null,
)

/**
 * JS-friendly facade over [HalHttpClient].
 * All HTTP methods return a [Promise<JsResponse>] so callers can use async/await.
 *
 * URI template expansion uses a builder pattern to avoid any encoding ambiguity:
 *   client.varsAdd("name", "My Project")   // one call per list item
 *   client.varsAdd("name", "Foo, Inc.")    // commas in values are fine
 *   client.varsSet("name_op", "in")        // scalar variable
 *   const url = client.expandVars(href)    // expand and reset the builder
 */
@JsExport
class JsHalClient {
    private val client = HalHttpClient()
    private var pendingVars = UriTemplateVars()

    // ── HTTP ──────────────────────────────────────────────────────────────────

    fun get(url: String, headers: JsHeaders? = null): Promise<JsResponse> = GlobalScope.promise {
        val r = client.get(url, headers?.toMap() ?: emptyMap()); JsResponse(r.statusCode, r.body)
    }

    fun post(url: String, json: String, headers: JsHeaders? = null): Promise<JsResponse> = GlobalScope.promise {
        val r = client.post(url, HalRequestBody.Json(json), headers?.toMap() ?: emptyMap()); JsResponse(r.statusCode, r.body)
    }

    fun patch(url: String, json: String, headers: JsHeaders? = null): Promise<JsResponse> = GlobalScope.promise {
        val r = client.patch(url, HalRequestBody.Json(json), headers?.toMap() ?: emptyMap()); JsResponse(r.statusCode, r.body)
    }

    fun delete(url: String, headers: JsHeaders? = null): Promise<JsResponse> = GlobalScope.promise {
        val r = client.delete(url, headers?.toMap() ?: emptyMap()); JsResponse(r.statusCode, r.body)
    }

    fun postFile(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: JsHeaders? = null,
    ): Promise<JsResponse> = GlobalScope.promise {
        val r = client.post(url, HalRequestBody.Binary(bytes, contentType), headers?.toMap() ?: emptyMap())
        JsResponse(r.statusCode, r.body)
    }

    fun postMultipart(
        url: String,
        parts: Array<JsMultipartPart>,
        headers: JsHeaders? = null,
    ): Promise<JsResponse> = GlobalScope.promise {
        val body = HalRequestBody.Multipart(parts.map {
            MultipartPart(it.name, it.bytes, it.fileName, it.contentType)
        })
        val r = client.post(url, body, headers?.toMap() ?: emptyMap())
        JsResponse(r.statusCode, r.body)
    }

    // ── URI template variable builder ─────────────────────────────────────────

    /** Discards all accumulated variables without expanding. */
    fun varsReset() { pendingVars = UriTemplateVars() }

    /** Sets a scalar variable. Replaces any existing value for [key]. */
    fun varsSet(key: String, value: String) { pendingVars.set(key, value) }

    /**
     * Appends one item to a list variable.
     * Call repeatedly with the same [key] to build a multi-value list.
     * Each value is an independent string — no escaping needed.
     */
    fun varsAdd(key: String, value: String) { pendingVars.add(key, value) }

    /**
     * Expands [href] using the variables accumulated by [varsSet]/[varsAdd],
     * then resets the builder for the next call.
     * With no variables accumulated, strips all template expressions.
     */
    fun expandVars(href: String): String =
        UriTemplate(href).expand(pendingVars).also { pendingVars = UriTemplateVars() }

    fun close() = client.close()
}
