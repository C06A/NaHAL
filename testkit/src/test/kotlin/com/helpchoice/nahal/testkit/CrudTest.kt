package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.http.HttpMethod
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The wrapper read as a sequence of HTTP calls, driven against [MockApi]. */
class CrudTest {

    // ── property extraction, coerced to plain Kotlin values ──────────────────────────────────

    @Test
    fun propertiesCoerceToKotlinTypes() {
        val root = MockApi.root()
        assertEquals("Root", root.prop("title"))
        assertEquals(3L, root["count"])
        assertEquals(true, root["active"])
        assertEquals(1.5, root["ratio"])
        assertNull(root["missing"])
        assertEquals(listOf("a", "b"), root["tags"])
        assertEquals(mapOf("k" to "v"), root["meta"])
        // _links / _embedded are not properties
        assertFalse(root.has("_links"))
        assertFalse(root.has("_embedded"))
    }

    // ── embedded by index and by key=value discriminator ─────────────────────────────────────

    @Test
    fun embeddedByIndexAndDiscriminator() {
        val root = MockApi.root()
        assertEquals(10L, root.embedded("orders", 0)!!["total"])
        assertEquals(42L, root.embedded("orders", 1)!!["total"])
        // discriminator: the order whose id == 2
        assertEquals(42L, root.embedded("orders", mapOf("id" to 2))!!["total"])
        // path form
        assertEquals(42L, root.at(PathStep.of("orders", mapOf("id" to 2)))!!["total"])
        assertNull(root.embedded("orders", mapOf("id" to 99)))
    }

    // ── following links: templated expansion ─────────────────────────────────────────────────

    @Test
    fun followsTemplatedLink() {
        val root = MockApi.root()
        val orders = root.send("GET", "orders", SendOptions(vars = mapOf("page" to 2))).asHal()
        assertEquals(2L, orders["page"])
    }

    // ── arbitrary (non-standard) HTTP method name ────────────────────────────────────────────

    @Test
    fun arbitraryMethodName() {
        val root = MockApi.root()
        val response = root.send("REPORT", "self")
        assertEquals(200, response.code)
        assertEquals("reported", response.asText())
    }

    // ── CURIE href expansion (ord:widget → http://api/orders/widget) ─────────────────────────

    @Test
    fun expandsCurieHref() {
        val root = MockApi.root()
        val widget = root.send("GET", "widget").asHal()
        assertEquals("widget", widget["kind"])
    }

    // ── SafeCURIE href expansion ([ord:widget] → http://api/orders/widget) ───────────────────

    @Test
    fun expandsSafeCurieHref() {
        val root = MockApi.root()
        val widget = root.send("GET", "safe").asHal()
        assertEquals("widget", widget["kind"])
    }

    // ── expandedHref: resolve a link's URL without sending (CURIE/SafeCURIE/template) ─────────

    @Test
    fun expandsHrefWithoutSending() {
        val root = MockApi.root()
        assertEquals("http://api/orders/widget", root.expandedHref("safe"))   // SafeCURIE
        assertEquals("http://api/orders/widget", root.expandedHref("widget")) // bare CURIE
        assertEquals("http://api/orders?page=2",
            root.expandedHref("orders", SendOptions(vars = mapOf("page" to 2))))
    }

    // ── documentation link resolved from `curies` for a rel ──────────────────────────────────

    @Test
    fun resolvesDocumentationFromCuries() {
        val root = MockApi.root()
        // bare local name — scans for a `<prefix>:orders` rel (doc:orders) then expands doc curie
        assertEquals("http://docs/orders", root.doc("orders")?.href)
        // explicit CURIE-prefixed rel
        assertEquals("http://docs/orders", root.doc("doc:orders")?.href)
        assertNull(root.doc("nope"))
    }

    // ── multipart upload ──────────────────────────────────────────────────────────────────────

    @Test
    fun multipartUpload() {
        val root = MockApi.root()
        val body = HalRequestBody.Multipart(
            listOf(
                MultipartPart("field", "value".encodeToByteArray()),
                MultipartPart("file", "data".encodeToByteArray(), fileName = "a.txt"),
            )
        )
        val response = root.send("POST", "uploads", SendOptions(body = body))
        assertEquals(201, response.code)
        assertEquals(true, response.asHal()["uploaded"])
    }

    // ── body as bytes / file ─────────────────────────────────────────────────────────────────

    @Test
    fun bodyAsBytesAndFile() {
        val root = MockApi.root()
        val response = root.send("REPORT", "self")
        assertContentEquals("reported".encodeToByteArray(), response.asBytes())

        val file = File.createTempFile("testkit", ".txt").also { it.deleteOnExit() }
        response.asFile(file)
        assertEquals("reported", file.readText())
    }

    // ── token session: refresh on 401, then retry ────────────────────────────────────────────

    @Test
    fun tokenSessionRefreshesOn401() {
        val creds = SimpleCredentials(tokenValue = "stale", onRefresh = { it.setToken("good") })
        val provider = MapCredentialsProvider(mapOf("alice" to creds))
        val session = TokenSession("alice", provider, refreshOn401 = true)
        val root = HalResource.from("http://api/", MockApi.context(session)).send("GET", "self").asHal()

        val secure = root.send("GET", "secure")
        assertEquals(200, secure.code)
        assertEquals("ok", secure.asHal()["secret"])
    }

    // ── token session: return the 401 as-is when refresh is disabled ─────────────────────────

    @Test
    fun tokenSessionReturns401AsIsWhenNotRefreshing() {
        val creds = SimpleCredentials(tokenValue = "stale", onRefresh = { it.setToken("good") })
        val provider = MapCredentialsProvider(mapOf("alice" to creds))
        val session = TokenSession("alice", provider, refreshOn401 = false)
        val root = HalResource.from("http://api/", MockApi.context(session)).send("GET", "self").asHal()

        val secure = root.send("GET", "secure")
        assertEquals(401, secure.code)
    }

    // ── modifiers as focused units ───────────────────────────────────────────────────────────

    @Test
    fun contentTypeModifierSetsHeaderFromLinkType() {
        val ctx = HalContext(MockApi.client())
        val link = HalLink(href = "http://api/things", type = "application/vnd.custom+json")
        val request = HalHttpRequest(
            url = "http://api/things",
            method = HttpMethod("POST"),
            body = HalRequestBody.Text("x"),
        )
        val modified = ContentTypeModifier().modify(request, link, ctx)
        assertEquals("application/vnd.custom+json", modified.headers["Content-Type"])
    }

    @Test
    fun curieModifierExpandsPrefix() {
        val ctx = HalContext(MockApi.client())
        val document = HalDocument(
            links = mapOf(
                "CURIE" to listOf(HalLink(href = "http://api/orders/", name = "ord")),
            )
        )
        val expanded = CurieModifier().modify(HalLink(href = "ord:widget"), "widget", document, ctx)
        assertEquals("http://api/orders/widget", expanded.href)
    }
}
