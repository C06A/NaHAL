package com.helpchoice.nahal.plugin.chain

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

/**
 * HALDiSh plugin combinator that passes every lifecycle hook through a sequence of plugins.
 *
 * Hooks are applied **in declaration order**:
 * - `initialize` — each plugin is initialised left-to-right.
 * - `preRequest`  — the request is threaded through all plugins left-to-right; each plugin
 *   receives the (possibly modified) output of the previous one.
 * - `postResponse` — the document is threaded through all plugins left-to-right.
 *
 * ### Usage
 * ```kotlin
 * val client = HalHttpClient(
 *     pluginOverride = ChainPlugin(
 *         BaseUrlRewriterPlugin("https://staging.example.com"),
 *         BearerTokenPlugin(token = System.getenv("API_TOKEN") ?: ""),
 *         LoggerPlugin(directory = "/tmp/hal-log"),
 *     )
 * )
 * ```
 *
 * The order matters: URL rewriting happens first, so the logger sees and records the
 * already-rewritten URL.
 *
 * @param plugins Plugins to chain, applied in the order given.
 */
class ChainPlugin(vararg plugins: HaldishPlugin) : HaldishPlugin {

    private val plugins: List<HaldishPlugin> = plugins.toList()

    override fun initialize(config: HaldishPluginConfig) =
        plugins.forEach { it.initialize(config) }

    override fun preLink(
        link: HalLink,
        rel: String,
        linkIndex: Int,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep>,
    ): HalLink =
        plugins.fold(link) { l, plugin -> plugin.preLink(l, rel, linkIndex, inDocument, embeddingPath) }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest =
        plugins.fold(request) { req, plugin -> plugin.preRequest(req) }

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument =
        plugins.fold(document) { doc, plugin -> plugin.postResponse(doc, response) }
}
