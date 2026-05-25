@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.helpchoice.nahal.haldish

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.JsAny
import kotlin.js.Promise
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

// Kotlin/Wasm restricts @JsExport to functions only (not classes), so the API
// is exposed as module-level functions with shared state — the same pattern as
// NativeCApi.kt.  Call wasmLastStatus() / wasmLastBody() after awaiting any
// HTTP Promise to read the result.

private val wasmClient = HalHttpClient()

// ── Last-response state ───────────────────────────────────────────────────────

private var lastStatus = 0
private var lastBody   = ""

@JsExport fun wasmLastStatus(): Int    = lastStatus
@JsExport fun wasmLastBody():   String = lastBody

// ── Request header builder ────────────────────────────────────────────────────
// Set headers before an HTTP call; they are consumed (and cleared) on the next call.

private val pendingHeaders = mutableMapOf<String, String>()

private fun consumeHeaders(): Map<String, String> =
    pendingHeaders.toMap().also { pendingHeaders.clear() }

@JsExport fun wasmHeaderSet(key: String, value: String) { pendingHeaders[key] = value }
@JsExport fun wasmHeadersClear() { pendingHeaders.clear() }

// ── HTTP ──────────────────────────────────────────────────────────────────────

@JsExport
fun wasmGet(url: String): Promise<JsAny?> = GlobalScope.promise {
    val r = wasmClient.get(url, consumeHeaders()); lastStatus = r.statusCode; lastBody = r.body; null
}

@JsExport
fun wasmPost(url: String, json: String): Promise<JsAny?> = GlobalScope.promise {
    val r = wasmClient.post(url, HalRequestBody.Json(json), consumeHeaders())
    lastStatus = r.statusCode; lastBody = r.body; null
}

@JsExport
fun wasmPatch(url: String, json: String): Promise<JsAny?> = GlobalScope.promise {
    val r = wasmClient.patch(url, HalRequestBody.Json(json), consumeHeaders())
    lastStatus = r.statusCode; lastBody = r.body; null
}

@JsExport
fun wasmDelete(url: String): Promise<JsAny?> = GlobalScope.promise {
    val r = wasmClient.delete(url, consumeHeaders()); lastStatus = r.statusCode; lastBody = r.body; null
}

@JsExport
fun wasmPostFile(url: String, bytes: Int8Array, contentType: String): Promise<JsAny?> = GlobalScope.promise {
    val r = wasmClient.post(url, HalRequestBody.Binary(ByteArray(bytes.length) { bytes[it] }, contentType), consumeHeaders())
    lastStatus = r.statusCode; lastBody = r.body; null
}

// ── Multipart builder ─────────────────────────────────────────────────────────
// Add parts one at a time, then call wasmPostMultipart to send them all.

private val pendingParts = mutableListOf<MultipartPart>()

@JsExport
fun wasmPartAdd(name: String, bytes: Int8Array, contentType: String, fileName: String?) {
    pendingParts.add(MultipartPart(name, ByteArray(bytes.length) { bytes[it] }, fileName, contentType))
}

@JsExport fun wasmPartsClear() { pendingParts.clear() }

@JsExport
fun wasmPostMultipart(url: String): Promise<JsAny?> = GlobalScope.promise {
    val parts = pendingParts.toList().also { pendingParts.clear() }
    val r = wasmClient.post(url, HalRequestBody.Multipart(parts), consumeHeaders())
    lastStatus = r.statusCode; lastBody = r.body; null
}

// ── URI template variable builder ─────────────────────────────────────────────

private var pendingVars = UriTemplateVars()

@JsExport fun wasmVarsReset()                           { pendingVars = UriTemplateVars() }
@JsExport fun wasmVarsSet(key: String, value: String)   { pendingVars.set(key, value) }
@JsExport fun wasmVarsAdd(key: String, value: String)   { pendingVars.add(key, value) }

@JsExport
fun wasmExpandVars(href: String): String =
    UriTemplate(href).expand(pendingVars).also { pendingVars = UriTemplateVars() }

// ── Lifecycle ─────────────────────────────────────────────────────────────────

@JsExport fun wasmClose() = wasmClient.close()
