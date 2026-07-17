package com.helpchoice.nahal.core

import com.helpchoice.nahal.core.plugin.buildConfiguredPlugin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.parser.HalParser
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars
import com.helpchoice.nahal.haldish.uritemplate.expandHref
import io.ktor.http.HttpMethod

class HalNavigator(
    private val client: HalHttpClient = HalHttpClient(pluginOverride = buildConfiguredPlugin()),
    val config: NavigatorConfig = NavigatorConfig(),
) : AutoCloseable {

    suspend fun navigate(
        resource: HalDocument,
        selector: LinkSelector,
        method: HttpMethod = HttpMethod.Get,
        templateVars: Map<String, Any> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        body: HalRequestBody = HalRequestBody.None,
    ): NavigationResponse {
        val baseLink = selector.select(resource) ?: throw NoSuchLinkException(selector)

        // HaldishPlugin.preLink — user-level hook with full document context via the path.
        val resolvedLink = client.resolveLink(baseLink, selector.toResourcePath(), resource)

        val url = resolvedLink.expandHref(templateVars.toUriTemplateVars())

        val request = HalHttpRequest(
            url = url,
            method = method,
            headers = config.defaultHeaders + headers,
            cookies = config.defaultCookies + cookies,
            body = body,
        )

        val rawResponse = client.execute(request)

        return NavigationResponse(raw = rawResponse, document = parseDocument(rawResponse, url), url = url)
    }

    /**
     * Executes a caller-assembled [RequestSpec]: resolves the target from its [RequestSpec.path]
     * (running `preLink` so plugins can create/modify the link), expands the URI template,
     * sends (`preRequest`), and parses a HAL response. Does no URL manipulation of its own —
     * relative hrefs are resolved only if a `preLink` plugin (e.g. base-url-rewriter) does so;
     * with the default no-op plugin, URLs are expected to be absolute.
     */
    /**
     * The URL [send] would request for [spec] — the target resolved through the `preLink` plugins
     * and template-expanded — without sending anything. Callers that need to *display* the outgoing
     * URL (a curl preview, or an error entry for a request that threw) must use this rather than
     * the raw href, which is still CURIE-prefixed / relative until the plugins have run.
     */
    fun resolveUrl(spec: RequestSpec): String =
        resolveLink(spec).expandHref(spec.templateVars.toUriTemplateVars())

    private fun resolveLink(spec: RequestSpec): HalLink = when {
        spec.path != null -> {
            val root = spec.rootDocument
                ?: throw IllegalArgumentException("RequestSpec.path requires a rootDocument")
            val target = spec.path.resolve(root)
                ?: throw IllegalArgumentException("RequestSpec.path did not resolve to a link: ${spec.path}")
            // preLink — plugins may create/modify the resolved link before it is sent.
            client.resolveLink(target.link, spec.path, root)
        }
        spec.url != null -> HalLink(href = spec.url)
        else -> throw IllegalArgumentException("RequestSpec requires either 'path' or 'url'")
    }

    suspend fun send(spec: RequestSpec): NavigationResponse {
        val url = resolveUrl(spec)

        val request = HalHttpRequest(
            url = url,
            method = spec.method,
            headers = config.defaultHeaders + spec.headers,
            cookies = config.defaultCookies + spec.cookies,
            body = spec.body,
            acceptHal = spec.acceptHal,
        )

        val rawResponse = client.execute(request)

        return NavigationResponse(raw = rawResponse, document = parseDocument(rawResponse, url), url = url)
    }

    /**
     * Parses the response body into a [HalDocument] whenever it is parseable — declared HAL or
     * not, so plain JSON/XML/YAML APIs still yield a navigable document (their fields become
     * properties). Null when the body isn't structured data. Unlike the `hal+*`-gated path this
     * feeds arbitrary bodies to the parsers, so any parser failure — not only [HalParseException]
     * — means "not a document" rather than an error.
     */
    private fun parseDocument(rawResponse: HalHttpResponse, url: String): HalDocument? =
        try {
            HalParser.parse(rawResponse.body, rawResponse.contentType).copy(sourceUrl = url)
        } catch (_: Exception) {
            null
        }

    override fun close() = client.close()
}

private fun Map<String, Any>.toUriTemplateVars(): UriTemplateVars =
    entries.fold(UriTemplateVars()) { vars, (key, value) ->
        when (value) {
            is List<*> -> vars.set(key, value)
            is Map<*, *> -> vars.set(key, value.mapKeys { it.key.toString() })
            else -> vars.set(key, value)
        }
    }
