package com.helpchoice.nahal.plugin.apikey

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyPluginTest {

    @Test
    fun addsDefaultHeader() {
        val plugin = ApiKeyPlugin(apiKey = "secret")
        val req = HalHttpRequest(url = "https://example.com")
        val modified = plugin.preRequest(req)
        assertEquals("secret", modified.headers["X-Api-Key"])
    }

    @Test
    fun addsCustomHeader() {
        val plugin = ApiKeyPlugin(apiKey = "tok", headerName = "Authorization")
        val req = HalHttpRequest(url = "https://example.com")
        val modified = plugin.preRequest(req)
        assertEquals("tok", modified.headers["Authorization"])
    }

    @Test
    fun preservesExistingHeaders() {
        val plugin = ApiKeyPlugin(apiKey = "key")
        val req = HalHttpRequest(url = "https://example.com", headers = mapOf("Accept" to "application/json"))
        val modified = plugin.preRequest(req)
        assertEquals("application/json", modified.headers["Accept"])
        assertEquals("key", modified.headers["X-Api-Key"])
    }

    @Test
    fun doesNotModifyOtherRequestFields() {
        val plugin = ApiKeyPlugin(apiKey = "key")
        val req = HalHttpRequest(url = "https://example.com/path")
        val modified = plugin.preRequest(req)
        assertEquals(req.url, modified.url)
        assertEquals(req.method, modified.method)
        assertEquals(req.acceptHal, modified.acceptHal)
    }
}
