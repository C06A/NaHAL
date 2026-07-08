package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

/**
 * A scripted HAL API served by Ktor's [MockEngine], so tests can express a sequence of HTTP calls
 * without a live server. Endpoints cover the wrapper's features: templated links, arbitrary
 * methods, CURIE expansion, multipart upload, and a `401`-guarded resource for session refresh.
 */
object MockApi {

    private const val HAL = "application/hal+json"

    private val ROOT = """
        {
          "title": "Root",
          "count": 3,
          "active": true,
          "ratio": 1.5,
          "missing": null,
          "tags": ["a", "b"],
          "meta": { "k": "v" },
          "_links": {
            "self":       { "href": "http://api/" },
            "orders":     { "href": "http://api/orders{?page}", "templated": true },
            "widget":     { "href": "ord:widget" },
            "safe":       { "href": "[ord:widget]" },
            "uploads":    { "href": "http://api/uploads" },
            "echo":       { "href": "http://api/echo" },
            "secure":     { "href": "http://api/secure" },
            "doc:orders": { "href": "http://api/orders" },
            "CURIE":      [ { "name": "ord", "href": "http://api/orders/" } ],
            "curies":     [ { "name": "doc", "href": "http://docs/{rel}", "templated": true } ]
          },
          "_embedded": {
            "orders": [
              { "id": 1, "total": 10, "_links": { "self": { "href": "http://api/orders/1" } } },
              { "id": 2, "total": 42, "_links": { "self": { "href": "http://api/orders/2" } } }
            ]
          }
        }
    """.trimIndent()

    fun client(): HalHttpClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            val page = request.url.parameters["page"]
            when {
                method == "REPORT" && path == "/" ->
                    respond("reported", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/plain"))

                method == "GET" && path == "/" ->
                    respond(ROOT, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, HAL))

                method == "GET" && path == "/orders" ->
                    respond("""{"page": ${page ?: "null"}, "_links": {"self": {"href": "http://api/orders?page=$page"}}}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, HAL))

                method == "GET" && path == "/orders/widget" ->
                    respond("""{"kind": "widget"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, HAL))

                // Echoes the request body bytes back — verifies binary/text/bytes file bodies.
                method == "POST" && path == "/echo" -> {
                    val ct = request.body.contentType?.toString() ?: "application/octet-stream"
                    val bytes = (request.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
                    respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ct))
                }

                method == "POST" && path == "/uploads" -> {
                    val isMultipart = request.body.contentType?.match(ContentType.MultiPart.FormData) == true
                    if (isMultipart) respond("""{"uploaded": true}""", HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, HAL))
                    else respond("""{"error": "expected multipart"}""", HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, HAL))
                }

                method == "GET" && path == "/secure" -> {
                    val auth = request.headers[HttpHeaders.Authorization]
                    if (auth == "Bearer good")
                        respond("""{"secret": "ok"}""", HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, HAL))
                    else
                        respond("""{"error": "unauthorized"}""", HttpStatusCode.Unauthorized,
                            headersOf(HttpHeaders.ContentType, HAL))
                }

                else -> respond("""{"error": "not found: $method $path"}""",
                    HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, HAL))
            }
        }
        return HalHttpClient(HttpClient(engine))
    }

    /** A context over a fresh mock client with the given [session]. */
    fun context(session: Session = NoSession): HalContext = HalContext(client(), session)

    /** The root resource, fetched and parsed, ready to navigate. */
    fun root(session: Session = NoSession): HalResource =
        HalResource.from("http://api/", context(session)).send("GET", "self").asHal()
}
