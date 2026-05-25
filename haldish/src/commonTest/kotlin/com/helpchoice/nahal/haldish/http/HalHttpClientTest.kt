package com.helpchoice.nahal.haldish.http

import com.helpchoice.nahal.haldish.HalHttpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HalHttpClientTest {

    private val halJson = """
        {
          "_links": { "self": { "href": "/orders" } },
          "count": 3
        }
    """.trimIndent()

    private fun mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = halJson,
        contentType: String = "application/hal+json",
    ): HalHttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status  = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }
        return HalHttpClient(HttpClient(engine))
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test fun getReturnsSuccess() = runTest {
        val response = mockClient().get("https://example.com/orders")
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
    }

    @Test fun getHalParsesDocument() = runTest {
        val doc = mockClient().getHal("https://example.com/orders")
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun getHalThrowsOnError() = runTest {
        assertFailsWith<HalHttpException> {
            mockClient(status = HttpStatusCode.NotFound, body = "Not Found", contentType = "text/plain")
                .getHal("https://example.com/missing")
        }
    }

    @Test fun getHalExceptionCarriesResponse() = runTest {
        val ex = assertFailsWith<HalHttpException> {
            mockClient(status = HttpStatusCode.NotFound, body = "Not Found", contentType = "text/plain")
                .getHal("https://example.com/missing")
        }
        assertEquals(404, ex.response.statusCode)
        assertEquals("Not Found", ex.response.body)
        assertEquals("text/plain", ex.response.contentType)
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test fun postWithTextBody() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(halJson, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        val response = HalHttpClient(HttpClient(engine))
            .post("https://example.com/orders", HalRequestBody.Text("hello"))
        assertEquals(201, response.statusCode)
        assertEquals("hello", capturedBody)
    }

    @Test fun postWithJsonBody() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(halJson, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine))
            .post("https://example.com/orders", HalRequestBody.Json("""{"x":1}"""))
        assertEquals("""{"x":1}""", capturedBody)
    }

    @Test fun postWithBinaryBody() = runTest {
        var capturedBytes: ByteArray? = null
        val engine = MockEngine { request ->
            capturedBytes = request.body.toByteArray()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine))
            .post("https://example.com/upload", HalRequestBody.Binary(byteArrayOf(1, 2, 3)))
        assertNotNull(capturedBytes)
        assertTrue(capturedBytes!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test fun postWithUrlEncodedBody() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine))
            .post("https://example.com/form", HalRequestBody.UrlEncoded(mapOf("key" to "value")))
        assertTrue(capturedBody?.contains("key=value") == true)
    }

    @Test fun postWithMultipartBodyWithFile() = runTest {
        val engine = MockEngine { _ ->
            respond(halJson, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        val response = HalHttpClient(HttpClient(engine)).post(
            "https://example.com/upload",
            HalRequestBody.Multipart(listOf(
                MultipartPart("file", byteArrayOf(1, 2), fileName = "photo.jpg", contentType = "image/jpeg")
            ))
        )
        assertEquals(201, response.statusCode)
    }

    @Test fun postWithMultipartBodyWithoutFile() = runTest {
        val engine = MockEngine { _ ->
            respond(halJson, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        val response = HalHttpClient(HttpClient(engine)).post(
            "https://example.com/upload",
            HalRequestBody.Multipart(listOf(
                MultipartPart("field", "hello".encodeToByteArray())
            ))
        )
        assertEquals(201, response.statusCode)
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test fun putSendsCorrectMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).put("https://example.com/orders/1", HalRequestBody.None)
        assertEquals(HttpMethod.Put, capturedMethod)
    }

    // ── PATCH ────────────────────────────────────────────────────────────────

    @Test fun patchSendsCorrectMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).patch("https://example.com/orders/1", HalRequestBody.Text("x"))
        assertEquals(HttpMethod.Patch, capturedMethod)
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test fun deleteSendsCorrectMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond("", HttpStatusCode.NoContent, headersOf())
        }
        HalHttpClient(HttpClient(engine)).delete("https://example.com/orders/1")
        assertEquals(HttpMethod.Delete, capturedMethod)
    }

    // ── OPTIONS ──────────────────────────────────────────────────────────────

    @Test fun optionsSendsCorrectMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).options("https://example.com/")
        assertEquals(HttpMethod.Options, capturedMethod)
    }

    // ── executeAndParse ───────────────────────────────────────────────────────

    @Test fun executeAndParseSuccess() = runTest {
        val doc = mockClient().executeAndParse(HalHttpRequest("https://example.com/"))
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun executeAndParseThrowsOn5xx() = runTest {
        assertFailsWith<HalHttpException> {
            mockClient(HttpStatusCode.InternalServerError, "err", "text/plain")
                .executeAndParse(HalHttpRequest("https://example.com/"))
        }
    }

    // ── Headers / cookies ─────────────────────────────────────────────────────

    @Test fun halAcceptHeaderSent() = runTest {
        var capturedAccept: String? = null
        val engine = MockEngine { request ->
            capturedAccept = request.headers[HttpHeaders.Accept]
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).get("https://example.com/")
        assertTrue(capturedAccept?.contains("hal+json") == true)
    }

    @Test fun noHalAcceptWhenDisabled() = runTest {
        var capturedAccept: String? = null
        val engine = MockEngine { request ->
            capturedAccept = request.headers[HttpHeaders.Accept]
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).execute(HalHttpRequest("https://example.com/", acceptHal = false))
        assertFalse(capturedAccept?.contains("hal+json") == true)
    }

    @Test fun customHeadersForwarded() = runTest {
        var capturedToken: String? = null
        val engine = MockEngine { request ->
            capturedToken = request.headers["Authorization"]
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).get("https://example.com/", headers = mapOf("Authorization" to "Bearer tok"))
        assertEquals("Bearer tok", capturedToken)
    }

    @Test fun singleCookieForwarded() = runTest {
        var capturedCookie: String? = null
        val engine = MockEngine { request ->
            capturedCookie = request.headers[HttpHeaders.Cookie]
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).get("https://example.com/", cookies = mapOf("session" to "abc"))
        assertEquals("session=abc", capturedCookie)
    }

    @Test fun multipleCookiesJoined() = runTest {
        var capturedCookie: String? = null
        val engine = MockEngine { request ->
            capturedCookie = request.headers[HttpHeaders.Cookie]
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine)).get(
            "https://example.com/",
            cookies = mapOf("a" to "1", "b" to "2"),
        )
        assertTrue(capturedCookie?.contains("a=1") == true)
        assertTrue(capturedCookie?.contains("b=2") == true)
    }

    @Test fun responseCookiesParsed() = runTest {
        val engine = MockEngine { _ ->
            respond(
                halJson,
                HttpStatusCode.OK,
                Headers.build {
                    append(HttpHeaders.ContentType, "application/hal+json")
                    append(HttpHeaders.SetCookie, "session=xyz; Path=/")
                },
            )
        }
        val response = HalHttpClient(HttpClient(engine)).get("https://example.com/")
        assertEquals("xyz", response.cookies["session"])
    }

    // ── close ────────────────────────────────────────────────────────────────

    @Test fun closeDoesNotThrow() {
        mockClient().close()
    }

    // ── HalHttpResponse ───────────────────────────────────────────────────────

    @Test fun isHalJson() {
        assertTrue(HalHttpResponse(200, emptyMap(), emptyMap(), "{}", "application/hal+json").isHal)
    }

    @Test fun isHalXml() {
        assertTrue(HalHttpResponse(200, emptyMap(), emptyMap(), "<r/>", "application/hal+xml").isHal)
    }

    @Test fun isHalYaml() {
        assertTrue(HalHttpResponse(200, emptyMap(), emptyMap(), "---", "application/hal+yaml").isHal)
    }

    @Test fun isNotHalForPlainJson() {
        assertFalse(HalHttpResponse(200, emptyMap(), emptyMap(), "{}", "application/json").isHal)
    }

    @Test fun isNotHalForNullContentType() {
        assertFalse(HalHttpResponse(200, emptyMap(), emptyMap(), "{}", null).isHal)
    }

    @Test fun isSuccessAt200() {
        assertTrue(HalHttpResponse(200, emptyMap(), emptyMap(), "", null).isSuccess)
    }

    @Test fun isSuccessAt299() {
        assertTrue(HalHttpResponse(299, emptyMap(), emptyMap(), "", null).isSuccess)
    }

    @Test fun isNotSuccessAt300() {
        assertFalse(HalHttpResponse(300, emptyMap(), emptyMap(), "", null).isSuccess)
    }

    @Test fun isNotSuccessAt199() {
        assertFalse(HalHttpResponse(199, emptyMap(), emptyMap(), "", null).isSuccess)
    }

    // ── HalRequestBody value equality ─────────────────────────────────────────

    @Test fun binaryEqualsSameBytes() {
        assertEquals(
            HalRequestBody.Binary(byteArrayOf(1, 2, 3)),
            HalRequestBody.Binary(byteArrayOf(1, 2, 3)),
        )
    }

    @Test fun binaryNotEqualsDifferentBytes() {
        assertFalse(
            HalRequestBody.Binary(byteArrayOf(1, 2, 3)) ==
            HalRequestBody.Binary(byteArrayOf(4, 5, 6))
        )
    }

    @Test fun binaryHashCodeConsistent() {
        val b = HalRequestBody.Binary(byteArrayOf(1, 2, 3))
        assertEquals(b.hashCode(), b.hashCode())
    }

    @Test fun multipartPartEqualsSameNameAndBytes() {
        assertEquals(
            MultipartPart("f", byteArrayOf(1, 2)),
            MultipartPart("f", byteArrayOf(1, 2)),
        )
    }

    @Test fun multipartPartNotEqualsDifferentName() {
        assertFalse(MultipartPart("a", byteArrayOf(1)) == MultipartPart("b", byteArrayOf(1)))
    }

    @Test fun multipartPartHashCodeConsistent() {
        val p = MultipartPart("name", byteArrayOf(1, 2, 3))
        assertEquals(p.hashCode(), p.hashCode())
    }
}
