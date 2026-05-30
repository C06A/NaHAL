package com.helpchoice.nahal.plugin.chain

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainPluginTest {

    private val dummyConfig = HaldishPluginConfig(platform = "test", version = "0.0.0")

    private val dummyResponse = HalHttpResponse(
        statusCode = 200,
        headers    = emptyMap(),
        cookies    = emptyMap(),
        body       = "{}",
        contentType = "application/hal+json",
    )

    private val dummyDocument = HalDocument(
        links      = emptyMap(),
        embedded   = emptyMap(),
        properties = JsonObject(emptyMap()),
    )

    /** A plugin that appends [tag] to `link.name` in [preLink]. */
    private fun linkTracePlugin(tag: String) = object : HaldishPlugin {
        override fun preLink(
            link: HalLink,
            rel: String,
            linkIndex: Int,
            inDocument: HalDocument,
            embeddingPath: List<EmbeddingStep>,
        ): HalLink = link.copy(name = (link.name ?: "") + tag)
    }

    /** A plugin that appends a string to a request header "X-Trace". */
    private fun tracePlugin(tag: String) = object : HaldishPlugin {
        var initialized = false
        override fun initialize(config: HaldishPluginConfig) { initialized = true }
        override fun preRequest(request: HalHttpRequest): HalHttpRequest =
            request.copy(headers = request.headers +
                ("X-Trace" to ((request.headers["X-Trace"] ?: "") + tag)))
        override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument =
            document.copy(properties = JsonObject(document.properties + ("trace" to
                kotlinx.serialization.json.JsonPrimitive((document.properties["trace"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "") + tag))))
    }

    @Test
    fun emptyChainIsNoOp() {
        val chain = ChainPlugin()
        val req = HalHttpRequest(url = "https://example.com")
        assertEquals(req, chain.preRequest(req))
        assertEquals(dummyDocument, chain.postResponse(dummyDocument, dummyResponse))
        val link = HalLink(href = "https://example.com")
        assertEquals(link, chain.preLink(link, "self", 0, dummyDocument, emptyList()))
    }

    @Test
    fun preLinkAppliedInOrder() {
        val chain = ChainPlugin(linkTracePlugin("A"), linkTracePlugin("B"), linkTracePlugin("C"))
        val link = HalLink(href = "https://example.com")
        val result = chain.preLink(link, "self", 0, dummyDocument, emptyList())
        assertEquals("ABC", result.name)
    }

    @Test
    fun preLinkPassesContextUnchanged() {
        val parentDoc = HalDocument()
        val step = EmbeddingStep("orders", 1, parentDoc)
        var capturedRel: String? = null
        var capturedLinkIndex: Int? = null
        var capturedDoc: HalDocument? = null
        var capturedPath: List<EmbeddingStep>? = null
        val spy = object : HaldishPlugin {
            override fun preLink(
                link: HalLink, rel: String, linkIndex: Int,
                inDocument: HalDocument, embeddingPath: List<EmbeddingStep>,
            ): HalLink {
                capturedRel = rel
                capturedLinkIndex = linkIndex
                capturedDoc = inDocument
                capturedPath = embeddingPath
                return link
            }
        }
        val chain = ChainPlugin(spy)
        chain.preLink(HalLink(href = "https://example.com"), "ea:orders", 2, dummyDocument, listOf(step))
        assertEquals("ea:orders", capturedRel)
        assertEquals(2, capturedLinkIndex)
        assertEquals(dummyDocument, capturedDoc)
        assertEquals(listOf(step), capturedPath)
    }

    @Test
    fun embeddingStepCarriesIndex() {
        val parentDoc = HalDocument()
        val step = EmbeddingStep(rel = "items", index = 3, inDocument = parentDoc)
        assertEquals("items", step.rel)
        assertEquals(3, step.index)
        assertEquals(parentDoc, step.inDocument)
    }

    @Test
    fun initializesAllPlugins() {
        val a = tracePlugin("A")
        val b = tracePlugin("B")
        val chain = ChainPlugin(a, b)
        chain.initialize(dummyConfig)
        assertTrue(a.initialized)
        assertTrue(b.initialized)
    }

    @Test
    fun preRequestAppliedInOrder() {
        val chain = ChainPlugin(tracePlugin("A"), tracePlugin("B"), tracePlugin("C"))
        val req = HalHttpRequest(url = "https://example.com")
        val result = chain.preRequest(req)
        assertEquals("ABC", result.headers["X-Trace"])
    }

    @Test
    fun postResponseAppliedInOrder() {
        val chain = ChainPlugin(tracePlugin("X"), tracePlugin("Y"))
        val result = chain.postResponse(dummyDocument, dummyResponse)
        val traceValue = (result.properties["trace"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertEquals("XY", traceValue)
    }
}
