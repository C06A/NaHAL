package com.helpchoice.nahal.haldish.http

import com.helpchoice.nahal.haldish.HalHttpException
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.parser.HalParser
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.loadPlugin
import com.helpchoice.nahal.haldish.plugin.platformPluginConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent

class HalHttpClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    /**
     * Optional plugin override — supply a plugin directly instead of relying on
     * platform discovery. Useful for testing or explicit programmatic configuration.
     * When `null` (the default), the platform-specific [loadPlugin] is used.
     */
    pluginOverride: HaldishPlugin? = null,
) : AutoCloseable {

    /**
     * The active plugin. Lazily initialised on first use so that C API callers
     * have a chance to call `haldish_plugin_register()` before the first HTTP
     * operation triggers discovery.
     */
    private val plugin: HaldishPlugin by lazy {
        if (pluginOverride != null) pluginOverride
        else loadPlugin().also { it.initialize(platformPluginConfig()) }
    }

    companion object {
        const val HAL_ACCEPT =
            "application/hal+json, application/hal+xml;q=0.9, " +
            "application/hal+yaml;q=0.8, application/json;q=0.7, " +
            "application/xml;q=0.6"
    }

    /**
     * Invokes [com.helpchoice.nahal.haldish.plugin.HaldishPlugin.preLink] for the given
     * link and document context, returning the (possibly modified) link.
     *
     * Called by higher-level navigation layers (e.g. `HalNavigator`) before URL expansion,
     * so that plugins have access to the originating [HalDocument] and embedding path.
     */
    fun resolveLink(
        link: HalLink,
        rel: String,
        linkIndex: Int,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep> = emptyList(),
    ): HalLink = plugin.preLink(link, rel, linkIndex, inDocument, embeddingPath)

    suspend fun execute(request: HalHttpRequest): HalHttpResponse {
        val actualRequest = plugin.preRequest(request)
        val ktorResponse: HttpResponse = httpClient.request(actualRequest.url) {
            method = actualRequest.method

            if (actualRequest.acceptHal) header(HttpHeaders.Accept, HAL_ACCEPT)
            actualRequest.headers.forEach { (k, v) -> header(k, v) }

            if (actualRequest.cookies.isNotEmpty()) {
                header(HttpHeaders.Cookie,
                    actualRequest.cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" })
            }

            when (val b = actualRequest.body) {
                is HalRequestBody.None       -> {}
                is HalRequestBody.Text       -> setBody(TextContent(b.content, ContentType.parse(b.contentType)))
                is HalRequestBody.Json       -> setBody(TextContent(b.content, ContentType.Application.Json))
                is HalRequestBody.Binary     -> setBody(ByteArrayContent(b.bytes, ContentType.parse(b.contentType)))
                is HalRequestBody.FilePath   -> setBody(ByteArrayContent(loadFileBytes(b.path), ContentType.parse(b.contentType)))
                is HalRequestBody.UrlEncoded -> setBody(FormDataContent(Parameters.build {
                    b.params.forEach { (k, v) -> append(k, v) }
                }))
                is HalRequestBody.Multipart  -> {
                    // Multipart is set via formData builder
                    setBody(io.ktor.client.request.forms.MultiPartFormDataContent(
                        io.ktor.client.request.forms.formData {
                            b.parts.forEach { part ->
                                if (part.fileName != null) {
                                    append(part.name, part.bytes,
                                        Headers.build {
                                            append(HttpHeaders.ContentDisposition,
                                                "filename=\"${part.fileName}\"")
                                            append(HttpHeaders.ContentType, part.contentType)
                                        })
                                } else {
                                    append(part.name, part.bytes)
                                }
                            }
                        }
                    ))
                }
            }
        }

        // Read raw bytes first, then the text view — Ktor's default in-memory body caching
        // lets both be taken from the same saved response.
        val rawBytes    = ktorResponse.readRawBytes()
        val body        = ktorResponse.bodyAsText()
        val status      = ktorResponse.status.value
        val contentType = ktorResponse.headers[HttpHeaders.ContentType]
        val headers     = ktorResponse.headers.entries()
            .associate { (k, v) -> k to v }
        val cookies     = ktorResponse.setCookie()
            .associate { it.name to it.value }

        return HalHttpResponse(
            statusCode  = status,
            headers     = headers,
            cookies     = cookies,
            body        = body,
            contentType = contentType,
            bytes       = rawBytes,
        )
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Get,
        headers = headers, cookies = cookies))

    suspend fun post(
        url: String,
        body: HalRequestBody = HalRequestBody.None,
        headers: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Post,
        headers = headers, body = body))

    suspend fun put(
        url: String,
        body: HalRequestBody = HalRequestBody.None,
        headers: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Put,
        headers = headers, body = body))

    suspend fun patch(
        url: String,
        body: HalRequestBody = HalRequestBody.None,
        headers: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Patch,
        headers = headers, body = body))

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Delete,
        headers = headers))

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HalHttpResponse = execute(HalHttpRequest(url = url, method = HttpMethod.Options,
        headers = headers))

    suspend fun getHal(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HalDocument {
        val response = get(url, headers)
        if (!response.isSuccess) throw HalHttpException(
            response = response,
            message  = "HTTP ${response.statusCode} from $url",
        )
        val document = HalParser.parse(response.body, response.contentType).copy(sourceUrl = url)
        return plugin.postResponse(document, response)
    }

    suspend fun executeAndParse(request: HalHttpRequest): HalDocument {
        val response = execute(request)
        if (!response.isSuccess) throw HalHttpException(
            response = response,
            message  = "HTTP ${response.statusCode} from ${request.url}",
        )
        val document = HalParser.parse(response.body, response.contentType).copy(sourceUrl = request.url)
        return plugin.postResponse(document, response)
    }

    override fun close() = httpClient.close()
}
