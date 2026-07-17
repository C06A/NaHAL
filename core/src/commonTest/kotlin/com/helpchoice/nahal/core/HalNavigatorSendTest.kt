package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HalNavigatorSendTest {

    /** MockEngine that records the request and replies with [body]. */
    private class Capture {
        var url: String? = null
        var method: String? = null
        var header: String? = null
        fun navigator(
            body: String = """{"_links":{"self":{"href":"https://api.example.com/root"}}}""",
            status: HttpStatusCode = HttpStatusCode.OK,
            contentType: String = "application/hal+json",
            plugin: HaldishPlugin? = null,
        ): HalNavigator {
            val engine = MockEngine { req ->
                url = req.url.toString()
                method = req.method.value
                header = req.headers["X-Test"]
                respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
            }
            return HalNavigator(HalHttpClient(HttpClient(engine), pluginOverride = plugin))
        }
    }

    // ── bare URL (address bar) ───────────────────────────────────────────────

    @Test fun sendWithRawUrlExecutesAndParses() = runTest {
        val cap = Capture()
        val response = cap.navigator().send(RequestSpec(url = "https://api.example.com/root"))
        assertEquals("https://api.example.com/root", cap.url)
        assertTrue(response.isSuccess)
        assertNotNull(response.document)
        assertEquals("https://api.example.com/root", response.url)
    }

    // ── followed link resolved through preLink (base-url-rewriter style) ─────

    @Test fun sendResolvesRelativeLinkViaPreLinkPlugin() = runTest {
        val rewriter = object : HaldishPlugin {
            override fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink =
                if (link.href.startsWith("/")) link.copy(href = "https://api.example.com" + link.href) else link
        }
        val cap = Capture()
        val doc = HalDocument(
            links = mapOf("next" to listOf(HalLink("/next"))),
            sourceUrl = "https://api.example.com/root",
        )
        cap.navigator(plugin = rewriter).send(RequestSpec(path = ResourcePath.link("next"), rootDocument = doc))
        assertEquals("https://api.example.com/next", cap.url)
    }

    // ── property URL: followed like a link, through preLink ──────────────────

    @Test fun sendFollowsPropertyUrlThroughPreLink() = runTest {
        // A CURIE-style plugin proves a property href gets the same plugin treatment as a link.
        val expander = object : HaldishPlugin {
            override fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink =
                if (link.href.startsWith("ord:"))
                    link.copy(href = "https://api.example.com/orders/" + link.href.removePrefix("ord:"))
                else link
        }
        val cap = Capture()
        val doc = HalDocument(
            properties = mapOf("widget" to JsonPrimitive("ord:widget")),
            sourceUrl = "https://api.example.com/root",
        )
        cap.navigator(plugin = expander)
            .send(RequestSpec(path = ResourcePath.property("widget"), rootDocument = doc))
        assertEquals("https://api.example.com/orders/widget", cap.url)
    }

    @Test fun resolveUrlForIndexedPropertyInArray() = runTest {
        val cap = Capture()
        val doc = HalDocument(
            properties = mapOf(
                "mirrors" to buildJsonArray {
                    add(JsonPrimitive("https://a.example.com/x")); add(JsonPrimitive("https://b.example.com/x"))
                },
            ),
        )
        val url = cap.navigator().resolveUrl(
            RequestSpec(path = ResourcePath(listOf(PathStep.Property("mirrors", 1))), rootDocument = doc),
        )
        assertEquals("https://b.example.com/x", url)
        assertNull(cap.url)
    }

    // ── resolveUrl: the URL send would request, without sending ──────────────

    @Test fun resolveUrlRunsPreLinkAndExpandsWithoutSending() = runTest {
        val rewriter = object : HaldishPlugin {
            override fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink =
                if (link.href.startsWith("/")) link.copy(href = "https://api.example.com" + link.href) else link
        }
        val cap = Capture()
        val doc = HalDocument(
            links = mapOf("next" to listOf(HalLink("/items{?page}", templated = true))),
            sourceUrl = "https://api.example.com/root",
        )
        val url = cap.navigator(plugin = rewriter).resolveUrl(
            RequestSpec(path = ResourcePath.link("next"), rootDocument = doc, templateVars = mapOf("page" to 2)),
        )
        assertEquals("https://api.example.com/items?page=2", url)
        assertNull(cap.url)   // nothing was sent
    }

    // ── bare URL skips preLink ───────────────────────────────────────────────

    @Test fun sendWithBareUrlSkipsPreLink() = runTest {
        var preLinkCalled = false
        val plugin = object : HaldishPlugin {
            override fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink {
                preLinkCalled = true; return link
            }
        }
        val cap = Capture()
        cap.navigator(plugin = plugin).send(RequestSpec(url = "https://api.example.com/x"))
        assertEquals("https://api.example.com/x", cap.url)
        assertTrue(!preLinkCalled)
    }

    // ── template expansion of a followed link ────────────────────────────────

    @Test fun sendExpandsTemplatedLink() = runTest {
        val cap = Capture()
        val doc = HalDocument(
            links = mapOf("search" to listOf(HalLink("https://api.example.com/items{?page,size}", templated = true))),
        )
        cap.navigator().send(
            RequestSpec(
                path = ResourcePath.link("search"),
                rootDocument = doc,
                templateVars = mapOf("page" to 2, "size" to 20),
            )
        )
        assertTrue(cap.url!!.contains("page=2"), "expanded url was ${cap.url}")
        assertTrue(cap.url!!.contains("size=20"))
    }

    // ── property terminal: URL taken from a resource property ────────────────

    @Test fun sendResolvesUrlFromProperty() = runTest {
        val cap = Capture()
        val doc = HalDocument(properties = mapOf("homepage" to JsonPrimitive("https://api.example.com/home")))
        cap.navigator().send(RequestSpec(path = ResourcePath(listOf(PathStep.Property("homepage"))), rootDocument = doc))
        assertEquals("https://api.example.com/home", cap.url)
    }

    // ── empty terminal resolves to the resource's self link ──────────────────

    @Test fun sendWithEmptyTerminalUsesSelfLink() = runTest {
        val cap = Capture()
        val doc = HalDocument(links = mapOf("self" to listOf(HalLink("https://api.example.com/self"))))
        cap.navigator().send(RequestSpec(path = ResourcePath.self(), rootDocument = doc))
        assertEquals("https://api.example.com/self", cap.url)
    }

    // ── method / headers / body pass through ─────────────────────────────────

    @Test fun sendPassesMethodHeadersBody() = runTest {
        val cap = Capture()
        cap.navigator().send(
            RequestSpec(
                url = "https://api.example.com/things",
                method = HttpMethod.Post,
                headers = mapOf("X-Test" to "yes"),
                body = HalRequestBody.Json("""{"a":1}"""),
                acceptHal = false,
            )
        )
        assertEquals("POST", cap.method)
        assertEquals("yes", cap.header)
    }

    // ── non-HAL response → no document ───────────────────────────────────────

    @Test fun sendNonHalResponseHasNullDocument() = runTest {
        val cap = Capture()
        val response = cap.navigator(body = "plain text", contentType = "text/plain")
            .send(RequestSpec(url = "https://api.example.com/txt"))
        assertNull(response.document)
        assertEquals("plain text", response.raw.body)
    }

    // ── plain JSON (no hal+ content type) still yields a document ────────────

    @Test fun sendPlainJsonResponseParsesIntoDocument() = runTest {
        val cap = Capture()
        val response = cap.navigator(
            body = """{"links":{"next":"https://api.example.com/page/2"},"count":39}""",
            contentType = "application/json",
        ).send(RequestSpec(url = "https://api.example.com/feed"))
        val doc = assertNotNull(response.document)
        // No _links/_embedded — every field is a property, including the "links" object.
        assertTrue(doc.links.isEmpty())
        assertEquals(setOf("links", "count"), doc.properties.keys)
    }

    // ── neither url nor path ─────────────────────────────────────────────────

    @Test fun sendRequiresUrlOrPath() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Capture().navigator().send(RequestSpec())
        }
    }

    // ── path without a rootDocument is rejected ──────────────────────────────

    @Test fun sendPathRequiresRootDocument() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Capture().navigator().send(RequestSpec(path = ResourcePath.link("next")))
        }
    }
}
