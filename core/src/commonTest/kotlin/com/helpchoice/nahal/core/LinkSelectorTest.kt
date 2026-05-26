package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkSelectorTest {

    private fun doc() = HalDocument(
        links = mapOf(
            "self" to listOf(HalLink("/a"), HalLink("/b")),
        ),
        embedded = mapOf(
            "items" to listOf(
                HalDocument(links = mapOf("self" to listOf(HalLink("/item/1"), HalLink("/item/1/alt")))),
                HalDocument(links = mapOf("self" to listOf(HalLink("/item/2")))),
            )
        ),
    )

    // ── TopLevel ──────────────────────────────────────────────────────────────

    @Test fun topLevelSelectsFirstLinkByDefault() {
        assertEquals("/a", LinkSelector.TopLevel("self").select(doc())?.href)
    }

    @Test fun topLevelSelectsByIndex() {
        assertEquals("/b", LinkSelector.TopLevel("self", index = 1).select(doc())?.href)
    }

    @Test fun topLevelReturnsNullForMissingRel() {
        assertNull(LinkSelector.TopLevel("missing").select(doc()))
    }

    @Test fun topLevelReturnsNullForOutOfBoundsIndex() {
        assertNull(LinkSelector.TopLevel("self", index = 99).select(doc()))
    }

    @Test fun topLevelToStringContainsRelAndIndex() {
        val s = LinkSelector.TopLevel("next", index = 2).toString()
        assertTrue(s.contains("next"))
        assertTrue(s.contains("2"))
    }

    // ── InEmbedded ────────────────────────────────────────────────────────────

    @Test fun inEmbeddedSelectsDefaultLinkInFirstEmbedded() {
        assertEquals("/item/1", LinkSelector.InEmbedded("items", linkRel = "self").select(doc())?.href)
    }

    @Test fun inEmbeddedSelectsByEmbeddedIndex() {
        assertEquals("/item/2", LinkSelector.InEmbedded("items", embeddedIndex = 1, linkRel = "self").select(doc())?.href)
    }

    @Test fun inEmbeddedSelectsByLinkIndex() {
        assertEquals("/item/1/alt", LinkSelector.InEmbedded("items", linkRel = "self", linkIndex = 1).select(doc())?.href)
    }

    @Test fun inEmbeddedReturnsNullForMissingEmbeddedRel() {
        assertNull(LinkSelector.InEmbedded("missing", linkRel = "self").select(doc()))
    }

    @Test fun inEmbeddedReturnsNullForMissingLinkRel() {
        assertNull(LinkSelector.InEmbedded("items", linkRel = "missing").select(doc()))
    }

    @Test fun inEmbeddedReturnsNullForOutOfBoundsEmbeddedIndex() {
        assertNull(LinkSelector.InEmbedded("items", embeddedIndex = 99, linkRel = "self").select(doc()))
    }
}
