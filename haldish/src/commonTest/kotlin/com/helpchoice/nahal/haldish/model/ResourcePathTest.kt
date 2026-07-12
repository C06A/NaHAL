package com.helpchoice.nahal.haldish.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResourcePathTest {

    private fun link(href: String) = HalLink(href = href)

    @Test fun topLevelLinkResolves() {
        val doc = HalDocument(links = mapOf("next" to listOf(link("https://x/next"))))
        val target = ResourcePath.link("next").resolve(doc)!!
        assertEquals("https://x/next", target.link.href)
        assertEquals(doc, target.container)
        assertEquals(listOf(doc), target.documents)
    }

    @Test fun linkIndexSelectsWithinRel() {
        val doc = HalDocument(links = mapOf("alt" to listOf(link("https://x/a"), link("https://x/b"))))
        assertEquals("https://x/b", ResourcePath.link("alt", 1).resolve(doc)!!.link.href)
    }

    @Test fun embeddedThenLinkResolvesAndBuildsDocChain() {
        val item = HalDocument(links = mapOf("self" to listOf(link("https://x/item"))))
        val root = HalDocument(embedded = mapOf("orders" to listOf(item)))
        val path = ResourcePath(listOf(PathStep.Embedded("orders", 0), PathStep.Link("self", 0)))
        val target = path.resolve(root)!!
        assertEquals("https://x/item", target.link.href)
        assertEquals(listOf(root, item), target.documents)
        assertEquals("self", path.terminalRel)
    }

    @Test fun itemThenLinkResolves() {
        val item = HalDocument(links = mapOf("self" to listOf(link("https://x/0"))))
        val root = HalDocument(items = listOf(item))
        val path = ResourcePath(listOf(PathStep.Item(0), PathStep.Link("self", 0)))
        assertEquals("https://x/0", path.resolve(root)!!.link.href)
    }

    @Test fun emptyTerminalUsesSelfLink() {
        val doc = HalDocument(links = mapOf("self" to listOf(link("https://x/self"))))
        assertEquals("https://x/self", ResourcePath.self().resolve(doc)!!.link.href)
        assertEquals("self", ResourcePath.self().terminalRel)
    }

    @Test fun nestedPropertyChainYieldsUrl() {
        val doc = HalDocument(
            properties = mapOf("data" to buildJsonObject { put("url", JsonPrimitive("https://x/prop")) }),
        )
        val path = ResourcePath(listOf(PathStep.Property("data"), PathStep.Property("url")))
        assertEquals("https://x/prop", path.resolve(doc)!!.link.href)
        assertEquals("url", path.terminalRel)
    }

    @Test fun templatedPropertyUrlIsMarkedTemplated() {
        val doc = HalDocument(properties = mapOf("tpl" to JsonPrimitive("https://x/items{?page}")))
        val target = ResourcePath(listOf(PathStep.Property("tpl"))).resolve(doc)!!
        assertEquals(true, target.link.templated)
    }

    @Test fun missingLinkResolvesToNull() {
        assertNull(ResourcePath.link("nope").resolve(HalDocument()))
    }

    @Test fun brokenEmbeddedHopResolvesToNull() {
        val path = ResourcePath(listOf(PathStep.Embedded("missing", 0), PathStep.Link("self", 0)))
        assertNull(path.resolve(HalDocument()))
    }

    @Test fun invalidGrammarLinkBeforeEmbeddedThrows() {
        assertFailsWith<IllegalArgumentException> {
            ResourcePath(listOf(PathStep.Link("a", 0), PathStep.Embedded("b", 0)))
        }
    }

    @Test fun invalidGrammarItemNotFirstThrows() {
        assertFailsWith<IllegalArgumentException> {
            ResourcePath(listOf(PathStep.Embedded("a", 0), PathStep.Item(0)))
        }
    }

    @Test fun invalidGrammarMixedTerminalThrows() {
        assertFailsWith<IllegalArgumentException> {
            ResourcePath(listOf(PathStep.Link("a", 0), PathStep.Property("b")))
        }
    }
}
