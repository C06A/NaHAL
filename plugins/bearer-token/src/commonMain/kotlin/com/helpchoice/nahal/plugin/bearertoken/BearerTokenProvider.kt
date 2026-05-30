package com.helpchoice.nahal.plugin.bearertoken

import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

/**
 * Supplies the bearer token (and optional header name) for each request.
 *
 * Implement this interface when you need dynamic token retrieval — for example an OAuth client
 * that silently refreshes an expired access token.  Register the implementation class's FQN
 * via [BearerTokenProviderRegistry] (non-JVM platforms) before the first request, then
 * reference it with `providerClass` in your plugin config:
 *
 * ```yaml
 * BearerTokenPlugin:
 *   providerClass: com.example.OAuthTokenProvider
 * ```
 *
 * [token] is called on every HTTP request, so it may perform a lightweight cache-check or
 * trigger a token refresh transparently.
 */
interface BearerTokenProvider {
    fun initialize(config: HaldishPluginConfig) {}
    fun headerName(): String = "Authorization"
    fun token(): String
}

/**
 * Registry used by non-JVM platforms (JS, WasmJS, Apple, Linux, MinGW) to look up a
 * [BearerTokenProvider] by fully-qualified class name.
 *
 * Call [register] at application startup before any HTTP request is made:
 * ```kotlin
 * BearerTokenProviderRegistry.register(
 *     "com.example.OAuthTokenProvider",
 *     OAuthTokenProvider(),
 * )
 * ```
 *
 * On JVM the registry is bypassed — providers are instantiated via reflection.
 */
object BearerTokenProviderRegistry {
    private val providers = mutableMapOf<String, BearerTokenProvider>()

    fun register(fqn: String, provider: BearerTokenProvider) {
        providers[fqn] = provider
    }

    internal fun find(fqn: String): BearerTokenProvider? = providers[fqn]
}
