package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.ResourcePath

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
     * The [path] addresses the target from [rootDocument]; the relation name is
     * [ResourcePath.terminalRel] and the document directly containing the link is
     * `path.documentsToContainer(rootDocument).last()`. Walk `path.documentsToContainer(rootDocument)`
     * to inspect ancestors (e.g. to find CURIs). A plugin may create or modify the link and
     * return it for the next plugin (or the client) to use.
     *
     * @param link         the link about to be followed (resolved from [path])
     * @param path         the [ResourcePath] to the followed link or property, relative to [rootDocument]
     * @param rootDocument the [HalDocument] the [path] is resolved against
     */
    fun preLink(
        link: HalLink,
        path: ResourcePath,
        rootDocument: HalDocument,
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
    val properties: Map<String, Any?> = emptyMap(),
)

/** Default no-op plugin — used when no plugin is discovered on any platform. */
internal object NoOpPlugin : HaldishPlugin
