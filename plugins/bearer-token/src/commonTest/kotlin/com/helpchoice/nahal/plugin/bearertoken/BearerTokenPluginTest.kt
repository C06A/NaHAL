package com.helpchoice.nahal.plugin.bearertoken

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BearerTokenPluginTest {

    @Test
    fun addsAuthorizationHeader() {
        val plugin = BearerTokenPlugin(staticToken = "my-secret-token")
        val req = HalHttpRequest(url = "https://example.com")
        val modified = plugin.preRequest(req)
        assertEquals("Bearer my-secret-token", modified.headers["Authorization"])
    }

    @Test
    fun preservesExistingHeaders() {
        val plugin = BearerTokenPlugin(staticToken = "tok")
        val req = HalHttpRequest(url = "https://example.com", headers = mapOf("X-Custom" to "value"))
        val modified = plugin.preRequest(req)
        assertEquals("value", modified.headers["X-Custom"])
        assertTrue(modified.headers["Authorization"]?.startsWith("Bearer ") == true)
    }

    @Test
    fun doesNotModifyUrl() {
        val plugin = BearerTokenPlugin(staticToken = "tok")
        val req = HalHttpRequest(url = "https://example.com/resource")
        val modified = plugin.preRequest(req)
        assertEquals(req.url, modified.url)
    }

    @Test
    fun loadsStaticTokenFromConfig() {
        val plugin = BearerTokenPlugin()
        plugin.initialize(HaldishPluginConfig(
            platform = "test",
            version = "0",
            properties = mapOf("token" to "cfg-tok", "headerName" to "X-Auth"),
        ))
        val modified = plugin.preRequest(HalHttpRequest(url = "https://example.com"))
        assertEquals("Bearer cfg-tok", modified.headers["X-Auth"])
    }

    @Test
    fun usesProviderForDynamicToken() {
        var current = "first"
        val fqn = "com.test.DynProvider"
        BearerTokenProviderRegistry.register(fqn, object : BearerTokenProvider {
            override fun token() = current
            override fun headerName() = "X-Token"
        })
        val plugin = BearerTokenPlugin()
        plugin.initialize(HaldishPluginConfig(
            platform = "test",
            version = "0",
            properties = mapOf("providerClass" to fqn),
        ))
        assertEquals("Bearer first",
            plugin.preRequest(HalHttpRequest(url = "https://example.com")).headers["X-Token"])
        current = "refreshed"
        assertEquals("Bearer refreshed",
            plugin.preRequest(HalHttpRequest(url = "https://example.com")).headers["X-Token"])
    }

    @Test
    fun providerHeaderNameOverridesDefault() {
        val fqn = "com.test.CustomHeaderProvider"
        BearerTokenProviderRegistry.register(fqn, object : BearerTokenProvider {
            override fun token() = "tok"
            override fun headerName() = "X-Bearer"
        })
        val plugin = BearerTokenPlugin()
        plugin.initialize(HaldishPluginConfig("test", "0", mapOf("providerClass" to fqn)))
        val modified = plugin.preRequest(HalHttpRequest(url = "https://example.com"))
        assertEquals("Bearer tok", modified.headers["X-Bearer"])
        assertEquals(null, modified.headers["Authorization"])
    }
}
