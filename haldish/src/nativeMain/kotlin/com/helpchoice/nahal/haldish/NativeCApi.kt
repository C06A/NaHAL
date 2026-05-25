@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

// C API for native targets.
// Each @CName annotation below produces one extern function in the generated header.
//
// Generated header location (after running a linkXxx task):
//   haldish/build/bin/<target>/<variant>Shared/libhaldish_api.h
//
// Example:
//   ./gradlew :haldish:linkReleaseSharedMacosX64
//   → haldish/build/bin/macosX64/releaseShared/libhaldish_api.h

package com.helpchoice.nahal.haldish

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.parser.HalParser
import com.helpchoice.nahal.haldish.plugin.CFunctionPluginAdapter
import com.helpchoice.nahal.haldish.plugin.NativePluginRegistrar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.readBytes
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import kotlinx.coroutines.runBlocking
import kotlin.native.CName

// ── Last-response state ───────────────────────────────────────────────────────
// Non-thread-safe; safe for sequential single-threaded use in example code.

private var lastStatus: Int = 0
private var lastContentType: String? = null

@CName("haldish_last_status")
fun lastStatus(): Int = lastStatus

@CName("haldish_last_content_type")
fun lastContentType(): String? = lastContentType

// ── Shared HTTP client ────────────────────────────────────────────────────────

private val haldishClient = HalHttpClient()

// ── Request builder state ─────────────────────────────────────────────────────

private var pendingHeaders = mutableMapOf<String, String>()
private var pendingParts   = mutableListOf<MultipartPart>()

private fun consumeHeaders(): Map<String, String> =
    pendingHeaders.toMap().also { pendingHeaders.clear() }

// ── Header builder ────────────────────────────────────────────────────────────
// Set headers before an HTTP call; they are consumed (and cleared) on the next call.

@CName("haldish_headers_set")
fun headersSet(key: String, value: String) { pendingHeaders[key] = value }

@CName("haldish_headers_clear")
fun headersClear() { pendingHeaders.clear() }

// ── HTTP ──────────────────────────────────────────────────────────────────────

@CName("haldish_get")
fun get(url: String): String? = runBlocking {
    val r = haldishClient.get(url, consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.body
}

@CName("haldish_post_json")
fun postJson(url: String, json: String): String? = runBlocking {
    val r = haldishClient.post(url, HalRequestBody.Json(json), consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.body
}

@CName("haldish_patch_json")
fun patchJson(url: String, json: String): String? = runBlocking {
    val r = haldishClient.patch(url, HalRequestBody.Json(json), consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.body
}

@CName("haldish_delete")
fun deleteResource(url: String): Int = runBlocking {
    val r = haldishClient.delete(url, consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.statusCode
}

/**
 * Posts raw bytes as a single-file upload.
 * [data] is an int8_t* in C; cast from uint8_t* with (int8_t*)ptr.
 */
@CName("haldish_post_file")
fun postFile(url: String, data: CPointer<ByteVar>?, size: Int, contentType: String): String? = runBlocking {
    if (data == null) return@runBlocking null
    val r = haldishClient.post(url, HalRequestBody.Binary(data.readBytes(size), contentType), consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.body
}

// ── Multipart builder ─────────────────────────────────────────────────────────
// Add parts one at a time, then call haldish_post_multipart to send them all.

/**
 * Appends one part to the pending multipart request.
 * [data] is an int8_t* in C; [fileName] may be null for non-file parts.
 */
@CName("haldish_part_add")
fun partAdd(name: String, data: CPointer<ByteVar>?, size: Int, contentType: String, fileName: String?) {
    if (data == null) return
    pendingParts.add(MultipartPart(name, data.readBytes(size), fileName, contentType))
}

@CName("haldish_parts_clear")
fun partsClear() { pendingParts.clear() }

@CName("haldish_post_multipart")
fun postMultipart(url: String): String? = runBlocking {
    val parts = pendingParts.toList().also { pendingParts.clear() }
    val r = haldishClient.post(url, HalRequestBody.Multipart(parts), consumeHeaders())
    lastStatus = r.statusCode; lastContentType = r.contentType; r.body
}

// ── HAL ───────────────────────────────────────────────────────────────────────

/** Returns the href of the given link relation in the HAL document. */
@CName("haldish_link_href")
fun linkHref(body: String, contentType: String?, rel: String): String? =
    HalParser.parse(body, contentType).link(rel)?.href

/** Returns the "self" href of the first embedded resource with the given relation. */
@CName("haldish_first_embedded_self")
fun firstEmbeddedSelf(body: String, contentType: String?, embeddedRel: String): String? =
    HalParser.parse(body, contentType).firstEmbedded(embeddedRel)?.link("self")?.href

// ── URI templates ─────────────────────────────────────────────────────────────

/** Expands a URI template with no variables (strips all template expressions). */
@CName("haldish_expand")
fun expand(templateStr: String): String = UriTemplate(templateStr).expand()

/**
 * Expands a URI template using the variables accumulated by [haldish_vars_set]
 * and [haldish_vars_add], then resets the builder for the next call.
 */
@CName("haldish_expand_vars")
fun expandVars(templateStr: String): String =
    UriTemplate(templateStr).expand(pendingVars).also { pendingVars = UriTemplateVars() }

// ── URI template variable builder ─────────────────────────────────────────────
// Build up variables one call at a time before passing them to haldish_expand_vars.
// Call haldish_vars_reset() to start fresh without expanding.

private var pendingVars = UriTemplateVars()

/** Discards all accumulated variables without expanding. */
@CName("haldish_vars_reset")
fun varsReset() { pendingVars = UriTemplateVars() }

/** Sets a scalar variable. Replaces any existing value for [key]. */
@CName("haldish_vars_set")
fun varsSet(key: String, value: String) { pendingVars.set(key, value) }

/**
 * Appends one item to a list variable.
 * Call repeatedly with the same [key] to build a multi-value list.
 * Each value is an independent string — no escaping needed.
 */
@CName("haldish_vars_add")
fun varsAdd(key: String, value: String) { pendingVars.add(key, value) }

// ── Plugin registration ───────────────────────────────────────────────────────
//
// Alternative to the dynamic-library discovery: register C function pointers
// directly before the first HTTP call.  The client initialises lazily, so as
// long as haldish_plugin_register() is called before haldish_get() / haldish_post_*()
// / etc., the plugin will be active for all subsequent requests.
//
// Callback signatures match those of the dynamic-library plugin:
//   init_fn(platform, version)
//   pre_request_fn(url, method, header_count, header_keys, header_vals,
//                  body, content_type, accept_hal) → const char* json_or_null
//   post_response_fn(link_count, link_rels, link_hrefs, link_templated,
//                    prop_count, prop_keys, prop_json_vals,
//                    status_code, resp_body, resp_content_type) → const char* json_or_null
//
// See PLUGIN_CONTRACT.md for the full specification.

@CName("haldish_plugin_register")
fun pluginRegister(
    initFn:    CPointer<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>>?,
    preReqFn:  CPointer<CFunction<(
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
        Int,
        CPointer<CPointerVar<ByteVar>>?,
        CPointer<CPointerVar<ByteVar>>?,
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
        Int,
    ) -> CPointer<ByteVar>?>>?,
    postResFn: CPointer<CFunction<(
        Int,
        CPointer<CPointerVar<ByteVar>>?,
        CPointer<CPointerVar<ByteVar>>?,
        CPointer<IntVar>?,
        Int,
        CPointer<CPointerVar<ByteVar>>?,
        CPointer<CPointerVar<ByteVar>>?,
        Int,
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
    ) -> CPointer<ByteVar>?>>?,
) {
    NativePluginRegistrar.setPlugin(CFunctionPluginAdapter(initFn, preReqFn, postResFn))
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

@CName("haldish_close")
fun close() = haldishClient.close()

// ── Formatting ────────────────────────────────────────────────────────────────

/** Returns an indented copy of a compact JSON string. */
@CName("haldish_pretty_json")
fun prettyJson(body: String): String = buildString {
    var indent = 0; var inStr = false; var escape = false
    for (c in body) {
        if (escape) { append(c); escape = false; continue }
        if (c == '\\' && inStr) { append(c); escape = true; continue }
        if (c == '"') inStr = !inStr
        if (inStr) { append(c); continue }
        when (c) {
            '{', '[' -> { append(c); append('\n'); repeat(++indent) { append("  ") } }
            '}', ']' -> { append('\n'); repeat(--indent) { append("  ") }; append(c) }
            ','      -> { append(c); append('\n'); repeat(indent) { append("  ") } }
            ':'      -> append(": ")
            ' ', '\t', '\n', '\r' -> {}
            else     -> append(c)
        }
    }
}
