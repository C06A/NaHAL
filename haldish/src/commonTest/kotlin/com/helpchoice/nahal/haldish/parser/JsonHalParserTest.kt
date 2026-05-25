package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.HalParseException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonHalParserTest {

    // ---------- invalid input ----------

    @Test fun invalidJsonThrows() {
        assertFailsWith<HalParseException> { HalParser.parse("not json", "application/hal+json") }
    }

    @Test fun primitiveRootThrows() {
        assertFailsWith<HalParseException> { HalParser.parse("42", "application/hal+json") }
    }

    // ---------- _links error paths ----------

    @Test fun linksNotObjectThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_links": [1, 2]}""", "application/hal+json")
        }
    }

    @Test fun linkPrimitiveValueThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_links": {"self": "not-a-link"}}""", "application/hal+json")
        }
    }

    @Test fun linkArrayElementNotObjectThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_links": {"items": ["not-a-link"]}}""", "application/hal+json")
        }
    }

    @Test fun linkMissingHrefThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_links": {"self": {}}}""", "application/hal+json")
        }
    }

    // ---------- _embedded error paths ----------

    @Test fun embeddedNotObjectThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_embedded": "wrong"}""", "application/hal+json")
        }
    }

    @Test fun embeddedPrimitiveValueThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_embedded": {"items": 42}}""", "application/hal+json")
        }
    }

    @Test fun embeddedArrayElementNotObjectThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""{"_embedded": {"items": [42]}}""", "application/hal+json")
        }
    }

    // ---------- array root ----------

    @Test fun arrayRootReturnsItems() {
        val doc = HalParser.parse(
            """[{"_links":{"self":{"href":"/a"}}},{"_links":{"self":{"href":"/b"}}}]""",
            "application/hal+json"
        )
        assertEquals(2, doc.items.size)
        assertEquals("/a", doc.items[0].link("self")?.href)
        assertEquals("/b", doc.items[1].link("self")?.href)
    }

    @Test fun arrayRootElementNotObjectThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("""[1, 2]""", "application/hal+json")
        }
    }

    // ---------- single embedded object (not array) ----------

    @Test fun singleEmbeddedObject() {
        val json = """{"_embedded":{"item":{"_links":{"self":{"href":"/item/1"}}}}}"""
        val doc = HalParser.parse(json, "application/hal+json")
        assertEquals(1, doc.embedded("item").size)
        assertEquals("/item/1", doc.embedded("item")[0].link("self")?.href)
    }

    // ---------- all optional link fields ----------

    @Test fun allLinkFieldsParsed() {
        val json = """
            {
              "_links": {
                "self": {
                  "href": "/res",
                  "templated": true,
                  "type": "application/json",
                  "name": "my-name",
                  "title": "My Title",
                  "hreflang": "en",
                  "profile": "/profiles/res",
                  "deprecation": "/deprecation"
                }
              }
            }
        """.trimIndent()
        val link = HalParser.parse(json, "application/hal+json").link("self")
        assertNotNull(link)
        assertEquals("/res", link.href)
        assertTrue(link.templated)
        assertEquals("application/json", link.type)
        assertEquals("my-name", link.name)
        assertEquals("My Title", link.title)
        assertEquals("en", link.hreflang)
        assertEquals("/profiles/res", link.profile)
        assertEquals("/deprecation", link.deprecation)
    }

    // ---------- null _links / _embedded are treated as empty ----------

    @Test fun nullLinksIsEmpty() {
        val doc = HalParser.parse("""{"_links": null}""", "application/hal+json")
        assertTrue(doc.links.isEmpty())
    }

    @Test fun nullEmbeddedIsEmpty() {
        val doc = HalParser.parse("""{"_embedded": null}""", "application/hal+json")
        assertTrue(doc.embedded.isEmpty())
    }

    // ---------- property types ----------

    @Test fun numericAndBoolProperties() {
        val doc = HalParser.parse("""{"count": 3, "active": true, "score": 1.5}""", "application/hal+json")
        assertEquals(JsonPrimitive(3), doc.properties["count"])
        assertEquals(JsonPrimitive(true), doc.properties["active"])
        assertEquals(JsonPrimitive(1.5), doc.properties["score"])
    }
}
