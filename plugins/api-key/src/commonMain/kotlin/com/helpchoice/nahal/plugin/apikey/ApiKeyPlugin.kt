package com.helpchoice.nahal.plugin.apikey

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

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
 * @param apiKey      The API key value to send.
 * @param headerName  HTTP header name to use (default `X-Api-Key`).
 */
class ApiKeyPlugin(
    val apiKey: String,
    val headerName: String = "X-Api-Key",
) : HaldishPlugin {

    override fun preRequest(request: HalHttpRequest): HalHttpRequest =
        request.copy(headers = request.headers + (headerName to apiKey))
}
