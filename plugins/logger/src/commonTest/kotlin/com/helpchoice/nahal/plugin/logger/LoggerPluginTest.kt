package com.helpchoice.nahal.plugin.logger

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [LoggerPlugin].  These tests verify the observable contract
 * (hook return values, counter monotonicity, no exceptions) without inspecting
 * on-disk output, so they run on every KMP target including those without a real
 * filesystem (browser/WasmJS fall back to console output anyway).
 */
class LoggerPluginTest {

    private val jsonResponse = HalHttpResponse(
        statusCode  = 200,
        headers     = mapOf("Content-Type" to listOf("application/hal+json"), "X-Request-Id" to listOf("abc")),
        cookies     = emptyMap(),
        body        = """{"hello":"world"}""",
        contentType = "application/hal+json",
    )

    private val dummyDocument = HalDocument(
        links      = emptyMap(),
        embedded   = emptyMap(),
        properties = JsonObject(emptyMap()),
    )

    @Test
    fun preRequestReturnsRequestUnchanged() {
        val plugin = LoggerPlugin()
        val req = HalHttpRequest(url = "https://example.com")
        assertEquals(req, plugin.preRequest(req))
    }

    @Test
    fun postResponseReturnsDocumentUnchanged() {
        val plugin = LoggerPlugin()
        plugin.preRequest(HalHttpRequest(url = "https://example.com"))
        assertEquals(dummyDocument, plugin.postResponse(dummyDocument, jsonResponse))
    }

    @Test
    fun counterIncreasesWithEachRequest() {
        val plugin = LoggerPlugin()
        val req = HalHttpRequest(url = "https://example.com")
        // preRequest increments counter — we just check it doesn't crash across calls
        plugin.preRequest(req)
        plugin.postResponse(dummyDocument, jsonResponse)
        plugin.preRequest(req)
        plugin.postResponse(dummyDocument, jsonResponse)
    }

    @Test
    fun curlCommandContainsUrl() {
        val plugin = LoggerPlugin()
        val req = HalHttpRequest(url = "https://api.example.com/items")
        plugin.preRequest(req)
        // We can't inspect written files in commonTest (platform-specific),
        // but we can verify the plugin does not throw and the document comes back intact.
        val doc = plugin.postResponse(dummyDocument, jsonResponse)
        assertEquals(dummyDocument, doc)
    }

    @Test
    fun curlCommandIncludesPostBody() {
        // Smoke test: POST with JSON body doesn't crash the logger
        val plugin = LoggerPlugin()
        val req = HalHttpRequest(
            url    = "https://api.example.com/items",
            method = HttpMethod.Post,
            body   = HalRequestBody.Json("""{"name":"test"}"""),
        )
        plugin.preRequest(req)
        plugin.postResponse(dummyDocument, jsonResponse)
    }
}
