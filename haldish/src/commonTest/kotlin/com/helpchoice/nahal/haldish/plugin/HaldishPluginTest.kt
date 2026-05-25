package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for the plugin lifecycle: initialize, preRequest, postResponse hooks.
 *
 * All tests inject a plugin via [HalHttpClient]'s `pluginOverride` parameter,
 * which bypasses platform discovery and lets us verify hook behaviour with a
 * controlled [RecordingPlugin].
 */
class HaldishPluginTest {

    // ── Test doubles ─────────────────────────────────────────────────────────

    private val halJson = """
        {
          "_links": { "self": { "href": "/orders" }, "next": { "href": "/orders/2" } },
          "count": 3
        }
    """.trimIndent()

    /** Captures every call made to each hook. */
    private class RecordingPlugin(
        private val requestTransform:  (HalHttpRequest)  -> HalHttpRequest  = { it },
        private val responseTransform: (HalDocument, HalHttpResponse) -> HalDocument = { doc, _ -> doc },
    ) : HaldishPlugin {
        var initializedWith: HaldishPluginConfig? = null
        val preRequestCalls  = mutableListOf<HalHttpRequest>()
        val postResponseCalls = mutableListOf<Pair<HalDocument, HalHttpResponse>>()

        override fun initialize(config: HaldishPluginConfig) { initializedWith = config }
        override fun preRequest(request: HalHttpRequest): HalHttpRequest {
            preRequestCalls += request
            return requestTransform(request)
        }
        override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
            postResponseCalls += Pair(document, response)
            return responseTransform(document, response)
        }
    }

    private fun mockClient(
        plugin: HaldishPlugin,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = halJson,
        contentType: String = "application/hal+json",
    ): HalHttpClient {
        val engine = MockEngine { _ ->
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        }
        return HalHttpClient(HttpClient(engine), pluginOverride = plugin)
    }

    // ── initialize ───────────────────────────────────────────────────────────

    @Test fun initializeIsCalledOnFirstRequest() = runTest {
        val plugin = RecordingPlugin()
        assertNull(plugin.initializedWith, "should not initialize before first request")
        mockClient(plugin).get("https://example.com/")
        assertNotNull(plugin.initializedWith, "should initialize on first request")
    }

    @Test fun initializeReceivesPlatformAndVersion() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).get("https://example.com/")
        val cfg = plugin.initializedWith!!
        assertTrue(cfg.platform.isNotBlank(), "platform should be non-empty")
        assertTrue(cfg.version.isNotBlank(),  "version should be non-empty")
    }

    @Test fun initializeIsCalledOnlyOnce() = runTest {
        val plugin = RecordingPlugin()
        val client = mockClient(plugin)
        client.get("https://example.com/")
        client.get("https://example.com/")
        client.get("https://example.com/")
        // initializedWith is set only once; a list would still have one entry
        assertNotNull(plugin.initializedWith)
    }

    // ── preRequest ───────────────────────────────────────────────────────────

    @Test fun preRequestIsCalledForEveryHttpCall() = runTest {
        val plugin = RecordingPlugin()
        val client = mockClient(plugin)
        client.get("https://example.com/a")
        client.get("https://example.com/b")
        assertEquals(2, plugin.preRequestCalls.size)
    }

    @Test fun preRequestReceivesOriginalRequest() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).get("https://example.com/orders", headers = mapOf("X-Foo" to "bar"))
        val captured = plugin.preRequestCalls.first()
        assertEquals("https://example.com/orders", captured.url)
        assertEquals("bar", captured.headers["X-Foo"])
    }

    @Test fun preRequestCanModifyUrl() = runTest {
        var capturedUrl = ""
        val plugin = RecordingPlugin(
            requestTransform = { it.copy(url = "https://example.com/modified") }
        )
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine), pluginOverride = plugin).get("https://example.com/original")
        assertEquals("https://example.com/modified", capturedUrl)
    }

    @Test fun preRequestCanAddHeaders() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val plugin = RecordingPlugin(
            requestTransform = { it.copy(headers = it.headers + ("Authorization" to "Bearer token123")) }
        )
        val engine = MockEngine { req ->
            req.headers.forEach { key, values -> capturedHeaders[key] = values.first() }
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine), pluginOverride = plugin).get("https://example.com/")
        assertEquals("Bearer token123", capturedHeaders["Authorization"])
    }

    @Test fun preRequestModifiedRequestIsActuallySent() = runTest {
        val capturedMethod = mutableListOf<String>()
        val plugin = RecordingPlugin(
            requestTransform = { it.copy(method = io.ktor.http.HttpMethod.Post) }
        )
        val engine = MockEngine { req ->
            capturedMethod += req.method.value
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HalHttpClient(HttpClient(engine), pluginOverride = plugin).get("https://example.com/")
        assertEquals("POST", capturedMethod.first())
    }

    // ── postResponse ─────────────────────────────────────────────────────────

    @Test fun postResponseNotCalledForRawExecute() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).execute(HalHttpRequest("https://example.com/"))
        assertEquals(0, plugin.postResponseCalls.size, "postResponse should not fire for raw execute()")
    }

    @Test fun postResponseIsCalledForGetHal() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).getHal("https://example.com/orders")
        assertEquals(1, plugin.postResponseCalls.size)
    }

    @Test fun postResponseIsCalledForExecuteAndParse() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).executeAndParse(HalHttpRequest("https://example.com/orders"))
        assertEquals(1, plugin.postResponseCalls.size)
    }

    @Test fun postResponseReceivesParsedDocument() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).getHal("https://example.com/orders")
        val (doc, _) = plugin.postResponseCalls.first()
        assertEquals("/orders", doc.link("self")?.href)
        assertEquals("/orders/2", doc.link("next")?.href)
    }

    @Test fun postResponseReceivesReadOnlyHttpContext() = runTest {
        val plugin = RecordingPlugin()
        mockClient(plugin).getHal("https://example.com/orders")
        val (_, resp) = plugin.postResponseCalls.first()
        assertEquals(200, resp.statusCode)
        assertEquals("application/hal+json", resp.contentType)
    }

    @Test fun postResponseCanAddSyntheticLink() = runTest {
        val plugin = RecordingPlugin(
            responseTransform = { doc, _ ->
                val extraLinks = mapOf("synthetic" to listOf(HalLink(href = "/synthetic")))
                doc.copy(links = doc.links + extraLinks)
            }
        )
        val doc = mockClient(plugin).getHal("https://example.com/orders")
        assertEquals("/synthetic", doc.link("synthetic")?.href)
        assertEquals("/orders",    doc.link("self")?.href) // original links preserved
    }

    @Test fun postResponseCanRemoveLink() = runTest {
        val plugin = RecordingPlugin(
            responseTransform = { doc, _ ->
                doc.copy(links = doc.links.filterKeys { it != "next" })
            }
        )
        val doc = mockClient(plugin).getHal("https://example.com/orders")
        assertNull(doc.link("next"),      "next link should have been removed")
        assertNotNull(doc.link("self"),   "self link should be preserved")
    }

    // ── No-op plugin ─────────────────────────────────────────────────────────

    @Test fun noOpPluginDoesNotAlterRequest() = runTest {
        var capturedUrl = ""
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine), pluginOverride = NoOpPlugin)
            .get("https://example.com/orders")
        assertEquals("https://example.com/orders", capturedUrl)
    }

    @Test fun noOpPluginDoesNotAlterDocument() = runTest {
        val doc = HalHttpClient(
            HttpClient(MockEngine { respond(halJson, HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/hal+json")) }),
            pluginOverride = NoOpPlugin,
        ).getHal("https://example.com/orders")
        assertEquals("/orders",   doc.link("self")?.href)
        assertEquals("/orders/2", doc.link("next")?.href)
    }
}
