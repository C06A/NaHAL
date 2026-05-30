package com.helpchoice.nahal.plugin.bearertoken

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

/**
 * WasmJS — HALDiSh plugin that injects `Authorization: Bearer <token>` into every request.
 *
 * **Per-platform authoring note:** this file is intentionally self-contained (no commonMain
 * implementation). Copy it as a starting point for a WasmJS plugin that needs platform-specific
 * logic (e.g. reading a token from `localStorage` or an async token refresh via JS interop).
 *
 * ## Config-file usage (recommended)
 *
 * ### Static token
 * ```yaml
 * BearerTokenPlugin:
 *   token: eyJhbGci...
 *   headerName: Authorization   # optional
 * ```
 *
 * ### Dynamic provider (re-authenticates at runtime)
 * ```yaml
 * BearerTokenPlugin:
 *   providerClass: com.example.OAuthTokenProvider
 * ```
 * Register the provider before `HalHttpClient` makes its first request:
 * ```kotlin
 * BearerTokenProviderRegistry.register("com.example.OAuthTokenProvider", OAuthTokenProvider())
 * ```
 *
 * ## Programmatic usage
 * ```kotlin
 * val client = HalHttpClient(pluginOverride = BearerTokenPlugin(staticToken = myToken))
 * ```
 *
 * @param staticToken       Default bearer token; overridden by config `token` property.
 * @param staticHeaderName  Default header name; overridden by config `headerName` property.
 */
class BearerTokenPlugin(
    private var staticToken: String = "",
    private var staticHeaderName: String = "Authorization",
) : HaldishPlugin {

    private var provider: BearerTokenProvider? = null

    override fun initialize(config: HaldishPluginConfig) {
        val fqn = config.properties["providerClass"]?.toString()
        if (fqn != null) {
            provider = loadProvider(fqn).also { it.initialize(config) }
        } else {
            config.properties["token"]?.toString()?.let { staticToken = it }
            config.properties["headerName"]?.toString()?.let { staticHeaderName = it }
        }
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        val p = provider
        val header = p?.headerName() ?: staticHeaderName
        val token  = p?.token()      ?: staticToken
        return request.copy(headers = request.headers + (header to "Bearer $token"))
    }
}

private fun loadProvider(fqn: String): BearerTokenProvider =
    BearerTokenProviderRegistry.find(fqn)
        ?: error("BearerTokenProvider '$fqn' not found — call BearerTokenProviderRegistry.register() before the first request")
