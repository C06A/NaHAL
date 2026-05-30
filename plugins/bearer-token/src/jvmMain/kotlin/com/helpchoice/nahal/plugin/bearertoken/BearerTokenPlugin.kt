package com.helpchoice.nahal.plugin.bearertoken

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

/**
 * JVM — HALDiSh plugin that injects `Authorization: Bearer <token>` into every request.
 *
 * **Per-platform authoring note:** this file is intentionally self-contained (no commonMain
 * implementation). Copy it as a starting point for a JVM plugin that needs platform-specific
 * logic (e.g. reading a token from the Java KeyStore or a Spring Environment).
 *
 * ## Config-file usage (recommended)
 *
 * ### Static token
 * ```yaml
 * com:
 *   helpchoice:
 *     nahal:
 *       plugin:
 *         bearertoken:
 *           BearerTokenPlugin:
 *             token: eyJhbGci...
 *             headerName: Authorization   # optional, default: Authorization
 * ```
 *
 * ### Dynamic provider (re-authenticates at runtime)
 * ```yaml
 * BearerTokenPlugin:
 *   providerClass: com.example.OAuthTokenProvider
 * ```
 * `OAuthTokenProvider` must implement [BearerTokenProvider] and have a no-arg constructor.
 * Its [BearerTokenProvider.token] is called on every request, so it can refresh transparently.
 *
 * ## Programmatic usage
 * ```kotlin
 * val client = HalHttpClient(pluginOverride = BearerTokenPlugin(token = myToken))
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

@Suppress("UNCHECKED_CAST")
private fun loadProvider(fqn: String): BearerTokenProvider {
    BearerTokenProviderRegistry.find(fqn)?.let { return it }
    val cls = try {
        Class.forName(fqn)
    } catch (_: ClassNotFoundException) {
        error("BearerTokenProvider '$fqn' not found on the classpath or in BearerTokenProviderRegistry")
    }
    if (!BearerTokenProvider::class.java.isAssignableFrom(cls))
        error("Class '$fqn' does not implement BearerTokenProvider")
    return try {
        cls.getDeclaredConstructor().newInstance() as BearerTokenProvider
    } catch (_: NoSuchMethodException) {
        error("BearerTokenProvider '$fqn' has no no-arg constructor")
    }
}
