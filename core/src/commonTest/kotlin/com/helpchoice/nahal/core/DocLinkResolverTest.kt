package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocLinkResolverTest {

    private fun curie(name: String, href: String) = HalLink(href = href, name = name)

    /** A document whose `curies` define the given prefix → template pairs. */
    private fun docWithCuries(vararg defs: Pair<String, String>, extraRels: Map<String, List<HalLink>> = emptyMap()) =
        HalDocument(
            links = mapOf(DocLinkResolver.CURIES_REL to defs.map { (n, h) -> curie(n, h) }) + extraRels,
        )

    @Test
    fun resolvesPrefixedRelFromCurrentDocument() {
        val doc = docWithCuries("doc" to "https://example.com/docs/{rel}")
        val link = DocLinkResolver.resolve("doc:orders", doc)
        assertEquals("https://example.com/docs/orders", link?.href)
    }

    @Test
    fun emittedLinkIsHtmlType() {
        val doc = docWithCuries("doc" to "https://example.com/docs/{rel}")
        assertEquals(DocLinkResolver.DOC_LINK_TYPE, DocLinkResolver.resolve("doc:orders", doc)?.type)
    }

    @Test
    fun resolvesBareLocalNameBySuffixScan() {
        // curies + a prefixed link relation sharing the local name.
        val doc = docWithCuries(
            "doc" to "https://example.com/docs/{rel}",
            extraRels = mapOf("doc:orders" to listOf(HalLink("/orders"))),
        )
        assertEquals("https://example.com/docs/orders", DocLinkResolver.resolve("orders", doc)?.href)
    }

    @Test
    fun walksAncestorsToRootForCurie() {
        // curies defined only on the root; the relation lives in a deep child.
        val root = docWithCuries("doc" to "https://example.com/docs/{rel}")
        val mid = HalDocument()
        val leaf = HalDocument()
        val link = DocLinkResolver.resolve("doc:part", inDocument = leaf, ancestors = listOf(mid, root))
        assertEquals("https://example.com/docs/part", link?.href)
    }

    @Test
    fun nearestDefinitionWins() {
        val root = docWithCuries("doc" to "https://root.example.com/{rel}")
        val leaf = docWithCuries("doc" to "https://leaf.example.com/{rel}")
        val link = DocLinkResolver.resolve("doc:orders", inDocument = leaf, ancestors = listOf(root))
        assertEquals("https://leaf.example.com/orders", link?.href)
    }

    @Test
    fun returnsNullForUnknownPrefix() {
        val doc = docWithCuries("doc" to "https://example.com/docs/{rel}")
        assertNull(DocLinkResolver.resolve("nope:orders", doc))
    }

    @Test
    fun returnsNullForBareNameWithNoPrefixedRel() {
        val doc = docWithCuries("doc" to "https://example.com/docs/{rel}")
        assertNull(DocLinkResolver.resolve("orders", doc))
    }
}
