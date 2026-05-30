package com.helpchoice.nahal.core

import com.helpchoice.nahal.core.plugin.buildConfiguredPlugin
import com.helpchoice.nahal.haldish.HalParseException
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.parser.HalParser
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
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

        // Build document context for HaldishPlugin.preLink
        val linkRel: String
        val linkIndex: Int
        val linkInDocument: HalDocument
        val linkEmbeddingPath: List<EmbeddingStep>
        when (selector) {
            is LinkSelector.TopLevel -> {
                linkRel = selector.rel
                linkIndex = selector.index
                linkInDocument = resource
                linkEmbeddingPath = emptyList()
            }
            is LinkSelector.InEmbedded -> {
                // Safe: baseLink non-null guarantees the embedded path exists
                linkRel = selector.linkRel
                linkIndex = selector.linkIndex
                linkInDocument = resource.embedded(selector.embeddedRel)[selector.embeddedIndex]
                linkEmbeddingPath = listOf(EmbeddingStep(selector.embeddedRel, selector.embeddedIndex, resource))
            }
            is LinkSelector.InItems -> {
                // Safe: baseLink non-null guarantees the item exists
                linkRel = selector.linkRel
                linkIndex = selector.linkIndex
                linkInDocument = resource.items[selector.itemIndex]
                linkEmbeddingPath = listOf(EmbeddingStep(LinkSelector.ITEMS_REL, selector.itemIndex, resource))
            }
        }

        // HaldishPlugin.preLink — user-level hook with full document context
        val resolvedLink = client.resolveLink(baseLink, linkRel, linkIndex, linkInDocument, linkEmbeddingPath)

        val url = resolvedLink.expandHref(templateVars.toUriTemplateVars())

        val request = HalHttpRequest(
            url = url,
            method = method,
            headers = config.defaultHeaders + headers,
            cookies = config.defaultCookies + cookies,
            body = body,
        )

        val rawResponse = client.execute(request)

        val document = if (rawResponse.isHal) {
            try {
                HalParser.parse(rawResponse.body, rawResponse.contentType).copy(sourceUrl = url)
            } catch (_: HalParseException) {
                null
            }
        } else {
            null
        }

        return NavigationResponse(raw = rawResponse, document = document)
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
