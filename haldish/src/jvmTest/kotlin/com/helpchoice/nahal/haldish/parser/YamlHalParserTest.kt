package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.HalParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YamlHalParserTest {

    private val halYaml = """
        _links:
          self:
            href: /orders
          next:
            href: /orders?page=2
          search:
            href: /orders{?q}
            templated: true
          ea:admin:
            - href: /admins/2
              title: Fred
            - href: /admins/5
              title: Kate
        _embedded:
          ea:order:
            - _links:
                self:
                  href: /orders/123
              total: 30
              status: shipped
        currentlyProcessing: 14
        shippedToday: 20
    """.trimIndent()

    // ---------- basic parsing ----------

    @Test fun parseSelfLink() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun parseLinksCount() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        assertEquals(setOf("self", "next", "search", "ea:admin"), doc.linkRels())
    }

    @Test fun parseTemplatedLink() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        val search = doc.link("search")
        assertNotNull(search)
        assertTrue(search.templated)
        assertEquals("/orders{?q}", search.href)
    }

    @Test fun parseArrayLinks() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        val admins = doc.links("ea:admin")
        assertEquals(2, admins.size)
        assertEquals("Fred", admins[0].title)
        assertEquals("Kate", admins[1].title)
    }

    @Test fun parseEmbedded() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        val orders = doc.embedded("ea:order")
        assertEquals(1, orders.size)
        assertEquals("/orders/123", orders[0].link("self")?.href)
    }

    @Test fun parseIntProperties() {
        val doc = HalParser.parse(halYaml, "application/yaml")
        assertEquals(JsonPrimitive(14L), doc.properties["currentlyProcessing"])
        assertEquals(JsonPrimitive(20L), doc.properties["shippedToday"])
    }

    // ---------- property type coercion ----------

    @Test fun boolProperty() {
        val doc = HalParser.parse("flag: true", "application/yaml")
        assertEquals(JsonPrimitive(true), doc.properties["flag"])
    }

    @Test fun falseProperty() {
        val doc = HalParser.parse("flag: false", "application/yaml")
        assertEquals(JsonPrimitive(false), doc.properties["flag"])
    }

    @Test fun nullProperty() {
        val doc = HalParser.parse("nothing: null", "application/yaml")
        assertEquals(JsonNull, doc.properties["nothing"])
    }

    @Test fun tildeNullProperty() {
        val doc = HalParser.parse("nothing: ~", "application/yaml")
        assertEquals(JsonNull, doc.properties["nothing"])
    }

    @Test fun floatProperty() {
        val doc = HalParser.parse("score: 3.14", "application/yaml")
        val prop = doc.properties["score"]
        assertNotNull(prop)
        assertEquals(3.14, (prop as JsonPrimitive).content.toDouble(), 0.001)
    }

    @Test fun listProperty() {
        val doc = HalParser.parse("tags:\n  - a\n  - b", "application/yaml")
        val arr = doc.properties["tags"] as? JsonArray
        assertNotNull(arr)
        assertEquals(2, arr.size)
    }

    @Test fun mapProperty() {
        val doc = HalParser.parse("meta:\n  version: 1", "application/yaml")
        val obj = doc.properties["meta"] as? JsonObject
        assertNotNull(obj)
        assertEquals(JsonPrimitive(1L), obj["version"])
    }

    // ---------- single embedded object (not list) ----------

    @Test fun singleEmbeddedObject() {
        val yaml = """
            _embedded:
              item:
                _links:
                  self:
                    href: /item/1
        """.trimIndent()
        val doc = HalParser.parse(yaml, "application/yaml")
        assertEquals(1, doc.embedded("item").size)
        assertEquals("/item/1", doc.embedded("item")[0].link("self")?.href)
    }

    // ---------- all optional link fields ----------

    @Test fun allLinkFieldsParsed() {
        val yaml = """
            _links:
              self:
                href: /res
                templated: true
                type: application/json
                name: my-name
                title: My Title
                hreflang: en
                profile: /profiles/res
                deprecation: /deprecation
        """.trimIndent()
        val link = HalParser.parse(yaml, "application/yaml").link("self")
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

    // ---------- no links / no embedded ----------

    @Test fun noLinksNoEmbedded() {
        val doc = HalParser.parse("name: test\nvalue: 42", "application/yaml")
        assertTrue(doc.links.isEmpty())
        assertTrue(doc.embedded.isEmpty())
    }

    // ---------- error paths ----------

    @Test fun invalidYamlThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse(":\n  bad: [unclosed", "application/yaml")
        }
    }

    @Test fun nonMappingRootThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("- item1\n- item2", "application/yaml")
        }
    }

    @Test fun linkMissingHrefThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("_links:\n  self:\n    title: no-href", "application/yaml")
        }
    }

    @Test fun linkListItemNotMappingThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("_links:\n  items:\n    - not-a-map", "application/yaml")
        }
    }

    @Test fun unexpectedLinkValueThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("_links:\n  self: just-a-scalar", "application/yaml")
        }
    }

    @Test fun embeddedListItemNotMappingThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("_embedded:\n  items:\n    - not-a-map", "application/yaml")
        }
    }

    @Test fun unexpectedEmbeddedValueThrows() {
        assertFailsWith<HalParseException> {
            HalParser.parse("_embedded:\n  items: just-a-scalar", "application/yaml")
        }
    }

    // ---------- content-type variants ----------

    @Test fun halPlusYamlContentType() {
        val doc = HalParser.parse("_links:\n  self:\n    href: /x", "application/hal+yaml")
        assertEquals("/x", doc.link("self")?.href)
    }

    @Test fun textYamlContentType() {
        val doc = HalParser.parse("_links:\n  self:\n    href: /x", "text/yaml")
        assertEquals("/x", doc.link("self")?.href)
    }
}
