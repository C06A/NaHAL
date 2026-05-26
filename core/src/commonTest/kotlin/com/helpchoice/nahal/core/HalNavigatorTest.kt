package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HalNavigatorTest {

    private val halJson = """{"_links":{"self":{"href":"https://api.example.com/root"}}}"""

    private val resource = HalDocument(
        links = mapOf(
            "self"  to listOf(HalLink("https://api.example.com/root")),
            "items" to listOf(HalLink("https://api.example.com/items{?page,size}", templated = true)),
        ),
        embedded = mapOf(
            "orders" to listOf(
                HalDocument(links = mapOf("self" to listOf(HalLink("https://api.example.com/orders/1"))))
            )
        ),
    )

    private fun mockNavigator(
        responseBody: String = halJson,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/hal+json",
        config: NavigatorConfig = NavigatorConfig(),
        plugins: List<NavigationPlugin> = emptyList(),
    ): HalNavigator {
        val engine = MockEngine { _ ->
            respond(responseBody, statusCode, headersOf(HttpHeaders.ContentType, contentType))
        }
        return HalNavigator(HalHttpClient(HttpClient(engine)), config, plugins)
    }

    private class RecordingPlugin(
        val transformLink: (HalLink) -> HalLink = { it },
        val transformResponse: (HalHttpResponse) -> HalHttpResponse = { it },
    ) : NavigationPlugin {
        val initConfigs = mutableListOf<NavigatorConfig>()
        val preRequestCalls = mutableListOf<Pair<HalLink, HalDocument>>()
        val postResponseCalls = mutableListOf<HalHttpResponse>()

        override fun initialize(config: NavigatorConfig) { initConfigs += config }
        override fun preRequest(link: HalLink, resource: HalDocument): HalLink =
            transformLink(link).also { preRequestCalls += link to resource }
        override fun postResponse(response: HalHttpResponse): HalHttpResponse =
            transformResponse(response).also { postResponseCalls += response }
    }

    // ── Navigation / link resolution ──────────────────────────────────────────

    @Test fun navigateFollowsTopLevelLink() = runTest {
        val response = mockNavigator().navigate(resource, LinkSelector.TopLevel("self"))
        assertNotNull(response.document)
        assertEquals("https://api.example.com/root", response.document!!.link("self")?.href)
    }

    @Test fun navigateFollowsEmbeddedLink() = runTest {
        val response = mockNavigator().navigate(
            resource, LinkSelector.InEmbedded("orders", linkRel = "self")
        )
        assertTrue(response.isSuccess)
    }

    @Test fun navigateThrowsWhenLinkNotFound() = runTest {
        assertFailsWith<NoSuchLinkException> {
            mockNavigator().navigate(resource, LinkSelector.TopLevel("missing"))
        }
    }

    // ── URI template ──────────────────────────────────────────────────────────

    @Test fun navigateExpandsTemplatedLink() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalNavigator(HalHttpClient(HttpClient(engine))).navigate(
            resource, LinkSelector.TopLevel("items"),
            templateVars = mapOf("page" to 1, "size" to 20),
        )
        assertTrue(capturedUrl!!.contains("page=1"), "Expected page=1 in $capturedUrl")
        assertTrue(capturedUrl!!.contains("size=20"), "Expected size=20 in $capturedUrl")
    }

    @Test fun navigateLeavesNonTemplatedLinkUnchanged() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalNavigator(HalHttpClient(HttpClient(engine))).navigate(resource, LinkSelector.TopLevel("self"))
        assertEquals("https://api.example.com/root", capturedUrl)
    }

    // ── HTTP method & body ────────────────────────────────────────────────────

    @Test fun navigateUsesSpecifiedMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { req ->
            capturedMethod = req.method
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalNavigator(HalHttpClient(HttpClient(engine))).navigate(
            resource, LinkSelector.TopLevel("self"), method = HttpMethod.Delete
        )
        assertEquals(HttpMethod.Delete, capturedMethod)
    }

    @Test fun navigatePassesBodyToClient() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { req ->
            capturedBody = req.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalNavigator(HalHttpClient(HttpClient(engine))).navigate(
            resource, LinkSelector.TopLevel("self"),
            method = HttpMethod.Post,
            body = HalRequestBody.Text("payload"),
        )
        assertEquals("payload", capturedBody)
    }

    // ── Headers & cookies ─────────────────────────────────────────────────────

    @Test fun navigateMergesDefaultAndRequestHeaders() = runTest {
        var capturedDefaultHeader: String? = null
        var capturedCustomHeader: String? = null
        val engine = MockEngine { req ->
            capturedDefaultHeader = req.headers["X-Default"]
            capturedCustomHeader  = req.headers["X-Custom"]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        val config = NavigatorConfig(defaultHeaders = mapOf("X-Default" to "default-value"))
        HalNavigator(HalHttpClient(HttpClient(engine)), config).navigate(
            resource, LinkSelector.TopLevel("self"),
            headers = mapOf("X-Custom" to "custom-value"),
        )
        assertEquals("default-value", capturedDefaultHeader)
        assertEquals("custom-value", capturedCustomHeader)
    }

    @Test fun navigateMergesDefaultAndRequestCookies() = runTest {
        var capturedCookieHeader: String? = null
        val engine = MockEngine { req ->
            capturedCookieHeader = req.headers[HttpHeaders.Cookie]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        val config = NavigatorConfig(defaultCookies = mapOf("session" to "abc"))
        HalNavigator(HalHttpClient(HttpClient(engine)), config).navigate(
            resource, LinkSelector.TopLevel("self"),
            cookies = mapOf("pref" to "dark"),
        )
        assertTrue(capturedCookieHeader!!.contains("session=abc"), "Expected session cookie in $capturedCookieHeader")
        assertTrue(capturedCookieHeader!!.contains("pref=dark"), "Expected pref cookie in $capturedCookieHeader")
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    @Test fun navigateReturnsHalDocumentForHalResponse() = runTest {
        val response = mockNavigator(responseBody = halJson, contentType = "application/hal+json")
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertNotNull(response.document)
    }

    @Test fun navigateReturnsNullDocumentForNonHalResponse() = runTest {
        val response = mockNavigator(responseBody = "plain text", contentType = "text/plain")
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertNull(response.document)
    }

    @Test fun navigateReturnsNullDocumentOnParseFailure() = runTest {
        val response = mockNavigator(responseBody = "not valid json", contentType = "application/hal+json")
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertNull(response.document)
    }

    // ── Plugin lifecycle ──────────────────────────────────────────────────────

    @Test fun pluginInitializeCalledOnConstruction() {
        val config = NavigatorConfig(properties = mapOf("key" to "value"))
        val plugin = RecordingPlugin()
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json")) }
        HalNavigator(HalHttpClient(HttpClient(engine)), config, listOf(plugin))
        assertEquals(1, plugin.initConfigs.size)
        assertEquals(config, plugin.initConfigs[0])
    }

    @Test fun pluginPreRequestReceivesLinkAndResource() = runTest {
        val plugin = RecordingPlugin()
        mockNavigator(plugins = listOf(plugin)).navigate(resource, LinkSelector.TopLevel("self"))
        assertEquals(1, plugin.preRequestCalls.size)
        val (link, doc) = plugin.preRequestCalls[0]
        assertEquals("https://api.example.com/root", link.href)
        assertEquals(resource, doc)
    }

    @Test fun pluginPreRequestModifiedLinkIsUsed() = runTest {
        val newHref = "https://api.example.com/redirected"
        var capturedUrl: String? = null
        val plugin = RecordingPlugin(transformLink = { HalLink(newHref) })
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalNavigator(HalHttpClient(HttpClient(engine)), plugins = listOf(plugin))
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertEquals(newHref, capturedUrl)
    }

    @Test fun pluginPostResponseReceivesResponse() = runTest {
        val plugin = RecordingPlugin()
        mockNavigator(responseBody = halJson, plugins = listOf(plugin))
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertEquals(1, plugin.postResponseCalls.size)
        assertEquals(halJson, plugin.postResponseCalls[0].body)
    }

    @Test fun pluginPostResponseModifiedResponseIsUsed() = runTest {
        val plugin = RecordingPlugin(transformResponse = { it.copy(contentType = "text/plain") })
        val response = mockNavigator(plugins = listOf(plugin))
            .navigate(resource, LinkSelector.TopLevel("self"))
        assertNull(response.document)
    }

    @Test fun multiplePluginsChainInOrder() = runTest {
        val order = mutableListOf<String>()
        val plugin1 = object : NavigationPlugin {
            override fun initialize(config: NavigatorConfig) {}
            override fun preRequest(link: HalLink, resource: HalDocument): HalLink { order += "pre1"; return link }
            override fun postResponse(response: HalHttpResponse): HalHttpResponse { order += "post1"; return response }
        }
        val plugin2 = object : NavigationPlugin {
            override fun initialize(config: NavigatorConfig) {}
            override fun preRequest(link: HalLink, resource: HalDocument): HalLink { order += "pre2"; return link }
            override fun postResponse(response: HalHttpResponse): HalHttpResponse { order += "post2"; return response }
        }
        mockNavigator(plugins = listOf(plugin1, plugin2)).navigate(resource, LinkSelector.TopLevel("self"))
        assertEquals(listOf("pre1", "pre2", "post1", "post2"), order)
    }
}
