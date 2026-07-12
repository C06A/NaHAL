package com.helpchoice.nahal.plugin.curie

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath
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
        rootDocument: HalDocument,
        path: ResourcePath = ResourcePath.link("product"),
    ): HalLink = plugin.preLink(
        link = HalLink(href = href),
        path = path,
        rootDocument = rootDocument,
    )

    /** Path descending `_embedded.orders[0]._embedded.items[0]` to a `product` link. */
    private val deepPath = ResourcePath(
        listOf(PathStep.Embedded("orders", 0), PathStep.Embedded("items", 0), PathStep.Link("product", 0)),
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
        val item = HalDocument()            // link's own document, no CURIE here
        val ordersDoc = HalDocument(embedded = mapOf("items" to listOf(item)))  // no CURIE here
        val root = docWithCuries("ord" to "https://api.example.com/orders/")
            .copy(embedded = mapOf("orders" to listOf(ordersDoc)))
        assertEquals(
            "https://api.example.com/orders/widget",
            follow("ord:widget", rootDocument = root, path = deepPath).href,
        )
    }

    @Test
    fun nearestAncestorDefinitionWins() {
        // Same prefix defined at two levels; the nearer one shadows the root.
        val item = HalDocument()
        val ordersDoc = docWithCuries("ord" to "https://orders.example.com/")
            .copy(embedded = mapOf("items" to listOf(item)))
        val root = docWithCuries("ord" to "https://root.example.com/")
            .copy(embedded = mapOf("orders" to listOf(ordersDoc)))
        assertEquals(
            "https://orders.example.com/widget",
            follow("ord:widget", rootDocument = root, path = deepPath).href,
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
        assertSame(link, plugin.preLink(link, ResourcePath.link("self"), doc))
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
    fun expandsSafeCurie() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("https://api.example.com/orders/widget", follow("[ord:widget]", doc).href)
    }

    @Test
    fun expandsSafeCurieFromAncestorDocument() {
        val item = HalDocument()
        val ordersDoc = HalDocument(embedded = mapOf("items" to listOf(item)))
        val root = docWithCuries("ord" to "https://api.example.com/orders/")
            .copy(embedded = mapOf("orders" to listOf(ordersDoc)))
        assertEquals(
            "https://api.example.com/orders/widget",
            follow("[ord:widget]", rootDocument = root, path = deepPath).href,
        )
    }

    @Test
    fun expandedSafeCurieKeepsReferenceVerbatim() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("https://api.example.com/orders/a/b?x=1", follow("[ord:a/b?x=1]", doc).href)
    }

    @Test
    fun passesThroughSafeCurieWithUnknownPrefix() {
        // Brackets are kept when there is nothing to expand to.
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("[nope:thing]", follow("[nope:thing]", doc).href)
    }

    @Test
    fun passesThroughBracketedHrefWithoutColon() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("[widget]", follow("[widget]", doc).href)
    }

    @Test
    fun passesThroughUnbalancedLeadingBracket() {
        // Only a fully bracketed href is a SafeCURIE; "[ord" is not a valid NCName prefix.
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("[ord:widget", follow("[ord:widget", doc).href)
    }

    @Test
    fun unbalancedTrailingBracketIsPartOfTheReference() {
        // Not a SafeCURIE, so this is the bare CURIE "ord" + reference "widget]".
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        assertEquals("https://api.example.com/orders/widget]", follow("ord:widget]", doc).href)
    }

    @Test
    fun doesNotModifyOtherLinkFields() {
        val doc = docWithCuries("ord" to "https://api.example.com/orders/")
        val link = HalLink(href = "ord:widget", title = "Widget", type = "application/hal+json")
        val out = plugin.preLink(link, ResourcePath.link("product"), doc)
        assertEquals("Widget", out.title)
        assertEquals("application/hal+json", out.type)
    }
}
