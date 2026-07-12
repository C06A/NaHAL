package com.helpchoice.nahal.plugin.apikey

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

/**
 * HALDiSh plugin that adds a static API-key header to every outgoing request.
 *
 * ### JVM (ServiceLoader)
 * Package as a JAR with `META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`
 * pointing to a subclass that hard-wires the key:
 * ```kotlin
 * class MyApiKeyPlugin : ApiKeyPlugin(apiKey = System.getenv("MY_API_KEY") ?: "")
 * ```
 *
 * ### All platforms (programmatic)
 * Pass directly as a [pluginOverride] when constructing [HalHttpClient]:
 * ```kotlin
 * val client = HalHttpClient(pluginOverride = ApiKeyPlugin(apiKey = "secret"))
 * ```
 * Or combine with other plugins via [com.helpchoice.nahal.plugin.chain.ChainPlugin].
 *
 * ### Config file
 * ```yaml
 * com:
 *   helpchoice:
 *     nahal:
 *       plugin:
 *         apikey:
 *           ApiKeyPlugin:
 *             apiKey: secret
 *             headerName: X-Api-Key   # optional, default: X-Api-Key
 * ```
 *
 * @param apiKey      The API key value to send; overridden by config `apiKey` property.
 * @param headerName  HTTP header name to use; overridden by config `headerName` property.
 */
class ApiKeyPlugin(
    apiKey: String = "",
    headerName: String = "X-Api-Key",
) : HaldishPlugin {

    var apiKey: String = apiKey
        private set

    var headerName: String = headerName
        private set

    override fun initialize(config: HaldishPluginConfig) {
        config.properties["apiKey"]?.toString()?.let { apiKey = it }
        config.properties["headerName"]?.toString()?.let { headerName = it }
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest =
        request.copy(headers = request.headers + (headerName to apiKey))
}
