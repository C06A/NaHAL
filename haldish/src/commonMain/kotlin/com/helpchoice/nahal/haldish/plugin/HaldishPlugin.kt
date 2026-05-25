package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink

/**
 * One step in the embedding path from the root [HalDocument] to the document directly
 * containing a followed link.
 *
 * @property rel        the relation name used to embed the sub-document
 * @property index      position of the embedded document within the [rel] array
 * @property inDocument the parent document in which [rel] is embedded
 */
data class EmbeddingStep(
    val rel: String,
    val index: Int,
    val inDocument: HalDocument,
)

/**
 * Runtime plugin contract for haldish. Four lifecycle hooks — implement only those you need.
 * All hooks have default no-op implementations.
 *
 * Discovery is platform-specific (no recompilation of the library required):
 * - **JVM**: JAR on `HALDISH_PLUGIN_PATH` (env var) or classpath via `ServiceLoader`
 * - **iOS / macOS Swift**: `HaldishPluginRegistry.setPlugin(…)` at app startup
 * - **JS / WasmJS**: `window.__haldishPlugin = {…}` in the HTML page before module load
 * - **Linux / Windows / macOS C API**: `HALDISH_PLUGIN_PATH` env var or `libhaldish_plugin` in lib-path;
 *   alternatively call `haldish_plugin_register(…)` with C function pointers
 *
 * See `PLUGIN_CONTRACT.md` for full details and code examples on each platform.
 */
interface HaldishPlugin {
    /**
     * Called once, lazily, on the first HTTP operation performed by [HalHttpClient].
     * Use this to read configuration, establish connections, or initialise shared state.
     */
    fun initialize(config: HaldishPluginConfig) {}

    /**
     * Called before following a link from a [HalDocument].
     * Fires only when navigation is triggered via a link relation, not for bare-URL calls
     * such as [com.helpchoice.nahal.haldish.http.HalHttpClient.get].
     *
     * Use this to inspect or modify the link based on document context — for example,
     * to resolve CURI prefixes, check [HalLink.name] or [HalLink.title],
     * or make routing decisions based on relation semantics.
     *
     * @param link          the link about to be followed
     * @param rel           the relation name (key in `_links`)
     * @param linkIndex     position of [link] within the `_links[rel]` array
     * @param inDocument    the [HalDocument] directly containing this link
     * @param embeddingPath ordered list of embedding steps from root to direct parent;
     *                      empty for top-level links, one entry per level of embedding.
     *                      A list (not a map) is used to support repeated relation names
     *                      (e.g. "items" embedded within "items").
     *                      Walk `embeddingPath.map { it.inDocument }` to find CURIs in ancestors.
     */
    fun preLink(
        link: HalLink,
        rel: String,
        linkIndex: Int,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep> = emptyList(),
    ): HalLink = link

    /**
     * Called before every HTTP request. Return the (possibly modified) request.
     *
     * All request fields are pre-parsed and available as typed properties — no manual
     * parsing is required. The modified request returned here is what actually gets sent.
     */
    fun preRequest(request: HalHttpRequest): HalHttpRequest = request

    /**
     * Called after a successful HAL response has been parsed into a [HalDocument].
     * Return the (possibly modified) document; the caller receives whatever you return.
     *
     * [response] is provided as read-only context (status code, headers, raw body,
     * content-type). The plugin may not modify it here.
     *
     * This hook is only invoked by [HalHttpClient.getHal] and
     * [HalHttpClient.executeAndParse]; raw [HalHttpClient.execute] calls do not trigger it.
     */
    fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument = document
}

/**
 * Startup context passed to [HaldishPlugin.initialize].
 *
 * @property platform  Runtime platform identifier: `"jvm"`, `"js"`, `"wasmjs"`,
 *                     `"macos"`, `"ios"`, `"linux"`, or `"windows"`.
 * @property version   Library version string, e.g. `"0.1.0-SNAPSHOT"`.
 */
data class HaldishPluginConfig(
    val platform: String,
    val version: String,
)

/** Default no-op plugin — used when no plugin is discovered on any platform. */
internal object NoOpPlugin : HaldishPlugin
