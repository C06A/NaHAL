package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.HalParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XmlHalParserTest {

    private val halXml = """
        <resource href="/orders">
          <link rel="next"   href="/orders?page=2"/>
          <link rel="search" href="/orders{?q}" templated="true"/>
          <resource rel="ea:order" href="/orders/123">
            <link rel="self" href="/orders/123"/>
            <status>shipped</status>
            <total>30.00</total>
          </resource>
          <resource rel="ea:order" href="/orders/124">
            <link rel="self" href="/orders/124"/>
            <status>processing</status>
          </resource>
          <currentlyProcessing>14</currentlyProcessing>
          <shippedToday>20</shippedToday>
        </resource>
    """.trimIndent()

    @Test fun parseSelfLinkFromHref() {
        val doc = XmlHalParser.parse(halXml)
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun parseNamedLink() {
        val doc = XmlHalParser.parse(halXml)
        assertEquals("/orders?page=2", doc.link("next")?.href)
    }

    @Test fun parseTemplatedLink() {
        val doc = XmlHalParser.parse(halXml)
        val search = doc.link("search")
        assertNotNull(search)
        assertTrue(search.templated)
    }

    @Test fun parseEmbeddedCount() {
        val doc = XmlHalParser.parse(halXml)
        assertEquals(2, doc.embedded("ea:order").size)
    }

    @Test fun parseEmbeddedSelfLink() {
        val doc = XmlHalParser.parse(halXml)
        assertEquals("/orders/123", doc.firstEmbedded("ea:order")?.link("self")?.href)
    }

    @Test fun parseEmbeddedProperties() {
        val doc = XmlHalParser.parse(halXml)
        val order = doc.firstEmbedded("ea:order")
        assertNotNull(order)
        assertEquals("shipped", order.properties["status"]?.toString()?.trim('"'))
    }

    @Test fun parseTopLevelProperties() {
        val doc = XmlHalParser.parse(halXml)
        assertNotNull(doc.properties["currentlyProcessing"])
        assertNotNull(doc.properties["shippedToday"])
    }

    @Test fun parseViaHalParser() {
        val doc = HalParser.parse(halXml, "application/hal+xml")
        assertEquals("/orders", doc.link("self")?.href)
    }

    @Test fun noduplicateSelfWhenBothHrefAndExplicitLinkPresent() {
        val xml = """<resource href="/foo"><link rel="self" href="/foo"/></resource>"""
        val doc = XmlHalParser.parse(xml)
        assertEquals(1, doc.links("self").size)
    }

    @Test fun linkMissingRelThrows() {
        assertFailsWith<HalParseException> {
            XmlHalParser.parse("""<resource><link href="/foo"/></resource>""")
        }
    }

    @Test fun linkMissingHrefThrows() {
        assertFailsWith<HalParseException> {
            XmlHalParser.parse("""<resource><link rel="self"/></resource>""")
        }
    }

    @Test fun emptyDocumentThrows() {
        assertFailsWith<HalParseException> {
            XmlHalParser.parse("")
        }
    }

    @Test fun embeddedResourceWithoutRelDefaultsToItem() {
        val xml = """<resource href="/root"><resource href="/child"/></resource>"""
        val doc = XmlHalParser.parse(xml)
        assertEquals(1, doc.embedded("item").size)
    }

    @Test fun allLinkAttributesParsed() {
        val xml = """<resource>
            <link rel="described-by" href="/schema"
                  type="application/json" name="main" title="Schema"
                  hreflang="en" profile="/profiles/p" deprecation="/dep"/>
        </resource>"""
        val link = XmlHalParser.parse(xml).link("described-by")
        assertNotNull(link)
        assertEquals("application/json", link.type)
        assertEquals("main", link.name)
        assertEquals("Schema", link.title)
        assertEquals("en", link.hreflang)
        assertEquals("/profiles/p", link.profile)
        assertEquals("/dep", link.deprecation)
    }
}
