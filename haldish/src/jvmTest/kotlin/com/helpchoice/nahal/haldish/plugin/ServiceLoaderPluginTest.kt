package com.helpchoice.nahal.haldish.plugin

import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * JVM-specific tests for the ServiceLoader-based plugin discovery.
 *
 * The test plugin [JvmTestPlugin] is discovered via the standard
 * `META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`
 * file in src/jvmTest/resources.
 */
class ServiceLoaderPluginTest {

    @Test fun serviceLoaderFindsTestPlugin() {
        val loaded = ServiceLoader.load(
            HaldishPlugin::class.java,
            Thread.currentThread().contextClassLoader,
        ).firstOrNull()
        assertNotNull(loaded, "ServiceLoader should find JvmTestPlugin via META-INF/services")
        assertEquals(JvmTestPlugin::class.java, loaded!!::class.java)
    }

    @Test fun serviceLoaderPluginIsInitializedAndHooksAreCalled() = runTest {
        JvmTestPlugin.reset()

        val plugin = ServiceLoader.load(
            HaldishPlugin::class.java,
            Thread.currentThread().contextClassLoader,
        ).first()

        val halJson = """{"_links":{"self":{"href":"/"}}}"""
        val engine = MockEngine { _ ->
            respond(halJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/hal+json"))
        }
        HalHttpClient(HttpClient(engine), pluginOverride = plugin)
            .getHal("https://example.com/")

        assertNotNull(JvmTestPlugin.initConfig, "initialize() should have been called")
        assertEquals("jvm", JvmTestPlugin.initConfig?.platform)
        assertEquals(1, JvmTestPlugin.preRequestCount,   "preRequest() should have been called once")
        assertEquals(1, JvmTestPlugin.postResponseCount, "postResponse() should have been called once")
    }
}

/**
 * Test plugin registered via META-INF/services.
 * Uses companion object state so the test can observe calls without DI.
 */
class JvmTestPlugin : HaldishPlugin {
    companion object {
        var initConfig: HaldishPluginConfig? = null
        var preRequestCount  = 0
        var postResponseCount = 0

        fun reset() { initConfig = null; preRequestCount = 0; postResponseCount = 0 }
    }

    override fun initialize(config: HaldishPluginConfig) { initConfig = config }
    override fun preRequest(request: HalHttpRequest): HalHttpRequest {
        preRequestCount++
        return request
    }
    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument {
        postResponseCount++
        return document
    }
}
