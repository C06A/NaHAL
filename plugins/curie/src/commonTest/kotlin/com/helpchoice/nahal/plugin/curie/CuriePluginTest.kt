package com.helpchoice.nahal.plugin.curie

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CuriePluginTest {

    private val plugin = CuriePlugin()

    /** A document whose CURIE collection defines the given prefix → base-url pairs. */
    private fun docWithCuries(vararg defs: Pair<String, String>): HalDocument =
        HalDocument(
            links = mapOf(
                CuriePlugin.CURIE_REL to defs.map { (name, href) -> HalLink(href = href, name = name) },
            ),
        )

    private fun follow(
        href: String,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep> = emptyList(),
    ): HalLink = plugin.preLink(
        link = HalLink(href = href),
        rel = "product",
        linkIndex = 0,
        inDocument = inDocument,
        embeddingPath = embeddingPath,
    )

    @Test
    fun expandsCurieFromSameDocument() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("https://api.example.com/orders/widget", follow("ord:widget", doc).href)
    }

    @Test
    fun expandsSingleDefinitionCurie() {
        val doc = docWithCuries("doc" to "https://docs.example.com/")
        assertEquals("https://docs.example.com/orders", follow("doc:orders", doc).href)
    }

    @Test
    fun searchesUpEmbeddingStackToRoot() {
        // CURIE defined only at the root; link lives two levels deep.
        val root = docWithCuries("ord" to "https://api.example.com/orders/")
        val ordersDoc = HalDocument()       // no CURIE here
        val item = HalDocument()            // link's own document, no CURIE here
        val path = listOf(
            EmbeddingStep(rel = "orders", index = 0, inDocument = root),
            EmbeddingStep(rel = "items", index = 0, inDocument = ordersDoc),
        )
        assertEquals(
            "https://api.example.com/orders/widget",
            follow("ord:widget", inDocument = item, embeddingPath = path).href,
        )
    }

    @Test
    fun nearestAncestorDefinitionWins() {
        // Same prefix defined at two levels; the nearer one shadows the root.
        val root = docWithCuries("ord" to "https://root.example.com/")
        val ordersDoc = docWithCuries("ord" to "https://orders.example.com/")
        val item = HalDocument()
        val path = listOf(
            EmbeddingStep(rel = "orders", index = 0, inDocument = root),
            EmbeddingStep(rel = "items", index = 0, inDocument = ordersDoc),
        )
        assertEquals(
            "https://orders.example.com/widget",
            follow("ord:widget", inDocument = item, embeddingPath = path).href,
        )
    }

    @Test
    fun firstMatchingDefinitionWins() {
        val doc = HalDocument(
            links = mapOf(
                CuriePlugin.CURIE_REL to listOf(
                    HalLink(href = "https://first.example.com/", name = "ord"),
                    HalLink(href = "https://second.example.com/", name = "ord"),
                ),
            ),
        )
        assertEquals("https://first.example.com/x", follow("ord:x", doc).href)
    }

    @Test
    fun passesThroughWhenNoColon() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        val link = HalLink(href = "/relative/path")
        assertSame(link, plugin.preLink(link, "self", 0, doc))
    }

    @Test
    fun passesThroughUnknownPrefix() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("nope:thing", follow("nope:thing", doc).href)
    }

    @Test
    fun passesThroughUrlSchemeWithoutMatchingCurie() {
        // "http" is a valid NCName but has no CURIE definition → left untouched.
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("http://example.com/page", follow("http://example.com/page", doc).href)
    }

    @Test
    fun passesThroughInvalidNcnamePrefix() {
        // A prefix starting with a digit is not a valid NCName.
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("1bad:thing", follow("1bad:thing", doc).href)
    }

    @Test
    fun preservesReferenceVerbatim() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("https://api.example.com/orders/a/b?x=1", follow("ord:a/b?x=1", doc).href)
    }

    @Test
    fun doesNotModifyOtherLinkFields() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        val link = HalLink(href = "ord:widget", title = "Widget", type = "application/hal+json")
        val out = plugin.preLink(link, "product", 0, doc)
        assertEquals("Widget", out.title)
        assertEquals("application/hal+json", out.type)
    }
}
