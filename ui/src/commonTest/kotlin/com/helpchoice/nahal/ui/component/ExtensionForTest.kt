package com.helpchoice.nahal.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionForTest {

    @Test
    fun svgBeatsXml() = assertEquals("svg", extensionFor("image/svg+xml"))

    @Test
    fun halJsonIsJson() = assertEquals("json", extensionFor("application/hal+json"))

    @Test
    fun halXmlIsXml() = assertEquals("xml", extensionFor("application/hal+xml"))

    @Test
    fun xhtmlBeatsXml() = assertEquals("html", extensionFor("application/xhtml+xml"))

    @Test
    fun parametersStripped() = assertEquals("html", extensionFor("text/html; charset=utf-8"))

    @Test
    fun plainTextIsTxt() = assertEquals("txt", extensionFor("text/plain"))

    @Test
    fun unknownTextIsTxt() = assertEquals("txt", extensionFor("text/whatever"))

    @Test
    fun yaml() = assertEquals("yaml", extensionFor("application/x-yaml"))

    @Test
    fun images() {
        assertEquals("png", extensionFor("image/png"))
        assertEquals("jpg", extensionFor("image/jpeg"))
        assertEquals("gif", extensionFor("image/gif"))
        assertEquals("webp", extensionFor("image/webp"))
    }

    @Test
    fun pdf() = assertEquals("pdf", extensionFor("application/pdf"))

    @Test
    fun csv() = assertEquals("csv", extensionFor("text/csv"))

    @Test
    fun octetStreamIsDat() = assertEquals("dat", extensionFor("application/octet-stream"))

    @Test
    fun nullIsDat() = assertEquals("dat", extensionFor(null))

    @Test
    fun caseInsensitive() = assertEquals("svg", extensionFor("Image/SVG+XML"))
}
