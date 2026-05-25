package com.helpchoice.nahal.haldish.parser

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HalParserTest {

    private val halJson = """
        {
          "_links": {
            "self":   { "href": "/orders" },
            "next":   { "href": "/orders?page=2" },
            "search": { "href": "/orders{?q}", "templated": true },
            "ea:admin": [
              { "href": "/admins/2", "title": "Fred" },
              { "href": "/admins/5", "title": "Kate" }
            ]
          },
          "_embedded": {
            "ea:order": [
              {
                "_links": { "self": { "href": "/orders/123" } },
                "total": 30.00,
                "status": "shipped"
              }
            ]
          },
          "currentlyProcessing": 14,
          "shippedToday": 20
        }
    """.trimIndent()

    @Test fun parseJsonLinksCount() {
        val doc = HalParser.parse(halJson, "application/hal+json")
        assertEquals(setOf("self", "next", "search", "ea:admin"), doc.linkRels())
    }

    @Test fun parseJsonSingleLink() {
        val doc = HalParser.parse(halJson)
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun parseJsonTemplatedLink() {
        val doc = HalParser.parse(halJson)
        val search = doc.link("search")
        assertNotNull(search)
        assertTrue(search.templated)
        assertEquals("/orders{?q}", search.href)
    }

    @Test fun parseJsonArrayLinks() {
        val doc = HalParser.parse(halJson)
        val admins = doc.links("ea:admin")
        assertEquals(2, admins.size)
        assertEquals("Fred", admins[0].title)
        assertEquals("Kate", admins[1].title)
    }

    @Test fun parseJsonEmbedded() {
        val doc = HalParser.parse(halJson)
        val orders = doc.embedded("ea:order")
        assertEquals(1, orders.size)
        assertEquals("/orders/123", orders[0].link("self")?.href)
    }

    @Test fun parseJsonProperties() {
        val doc = HalParser.parse(halJson)
        assertEquals(JsonPrimitive(14), doc.properties["currentlyProcessing"])
        assertEquals(JsonPrimitive(20), doc.properties["shippedToday"])
    }

    @Test fun parseJsonNoLinksNoEmbedded() {
        val doc = HalParser.parse("""{"name":"test","value":42}""")
        assertTrue(doc.links.isEmpty())
        assertTrue(doc.embedded.isEmpty())
        assertEquals(2, doc.properties.size)
    }

    // Format detection
    @Test fun detectJsonFromContentType() =
        assertEquals(HalFormat.JSON, HalFormatDetector.detect("application/hal+json; charset=utf-8", "{}"))

    @Test fun detectXmlFromContentType() =
        assertEquals(HalFormat.XML, HalFormatDetector.detect("application/hal+xml", "<resource/>"))

    @Test fun detectJsonFromContent() =
        assertEquals(HalFormat.JSON, HalFormatDetector.detect(null, "{\"x\":1}"))

    @Test fun detectXmlFromContent() =
        assertEquals(HalFormat.XML, HalFormatDetector.detect(null, "<resource href=\"/\"/>"))

    @Test fun detectYamlFromContentType() =
        assertEquals(HalFormat.YAML, HalFormatDetector.detect("application/yaml", "x: 1"))

    @Test fun detectJsonFromApplicationJsonContentType() =
        assertEquals(HalFormat.JSON, HalFormatDetector.detect("application/json", "{}"))

    @Test fun detectXmlFromApplicationXmlContentType() =
        assertEquals(HalFormat.XML, HalFormatDetector.detect("application/xml", "<r/>"))

    @Test fun detectXmlFromTextXmlContentType() =
        assertEquals(HalFormat.XML, HalFormatDetector.detect("text/xml", "<r/>"))

    @Test fun detectYamlFromHalPlusYamlContentType() =
        assertEquals(HalFormat.YAML, HalFormatDetector.detect("application/hal+yaml", "x: 1"))

    @Test fun detectYamlFromTextYamlContentType() =
        assertEquals(HalFormat.YAML, HalFormatDetector.detect("text/yaml", "x: 1"))

    @Test fun unknownContentTypeFallsBackToBodyDetection() =
        assertEquals(HalFormat.JSON, HalFormatDetector.detect("text/plain", "{}"))

    @Test fun detectJsonFromArrayBody() =
        assertEquals(HalFormat.JSON, HalFormatDetector.detect(null, "[1,2,3]"))

    @Test fun detectYamlFromYamlBody() =
        assertEquals(HalFormat.YAML, HalFormatDetector.detect(null, "key: value"))

    @Test fun emptyBodyIsUnknown() =
        assertEquals(HalFormat.UNKNOWN, HalFormatDetector.detect(null, ""))

    @Test fun unknownFormatFallsBackToJsonParser() {
        // empty body → UNKNOWN format → delegates to JSON → throws parse error
        assertFailsWith<com.helpchoice.nahal.haldish.HalParseException> {
            HalParser.parse("")
        }
    }
}
