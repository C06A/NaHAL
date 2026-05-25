package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.HalParseException
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlinx.serialization.json.JsonPrimitive
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

internal object XmlHalParser {

    fun parse(body: String): HalDocument {
        return try {
            val reader = xmlStreaming.newReader(body)
            try {
                // advance to root element
                while (reader.hasNext()) {
                    val evt = reader.next()
                    if (evt == EventType.START_ELEMENT) return parseResource(reader)
                }
                throw HalParseException("No root element found in XML")
            } finally {
                reader.close()
            }
        } catch (e: HalParseException) {
            throw e
        } catch (e: Exception) {
            throw HalParseException("XML parse error: ${e.message}", e)
        }
    }

    private fun parseResource(reader: nl.adaptivity.xmlutil.XmlReader): HalDocument {
        val links    = mutableMapOf<String, MutableList<HalLink>>()
        val embedded = mutableMapOf<String, MutableList<HalDocument>>()
        val props    = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

        // Save href for use as fallback self link if no explicit <link rel="self"> is present.
        val hrefSelf = reader.getAttributeValue(null, "href")

        val depth = 1
        var currentDepth = depth

        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    currentDepth++
                    when (reader.localName) {
                        "link" -> {
                            val (rel, link) = parseLinkElement(reader)
                            links.getOrPut(rel) { mutableListOf() }.add(link)
                            currentDepth-- // parseLinkElement consumes END_ELEMENT
                        }
                        "resource" -> {
                            val rel = reader.getAttributeValue(null, "rel") ?: "item"
                            val doc = parseResource(reader)
                            embedded.getOrPut(rel) { mutableListOf() }.add(doc)
                            currentDepth-- // parseResource consumed END_ELEMENT
                        }
                        else -> {
                            val localName = reader.localName
                            val text = collectText(reader)
                            currentDepth--
                            props[localName] = JsonPrimitive(text)
                        }
                    }
                }
                EventType.END_ELEMENT -> {
                    currentDepth--
                    if (currentDepth < depth) break
                }
                else -> {}
            }
        }

        if (hrefSelf != null && !links.containsKey("self")) {
            links["self"] = mutableListOf(HalLink(href = hrefSelf))
        }

        return HalDocument(links = links, embedded = embedded, properties = props)
    }

    private fun parseLinkElement(reader: nl.adaptivity.xmlutil.XmlReader): Pair<String, HalLink> {
        val rel         = reader.getAttributeValue(null, "rel")
            ?: throw HalParseException("<link> element missing 'rel' attribute")
        val href        = reader.getAttributeValue(null, "href")
            ?: throw HalParseException("<link> element missing 'href' attribute")
        val templated   = reader.getAttributeValue(null, "templated")?.lowercase() == "true"
        val type        = reader.getAttributeValue(null, "type")
        val name        = reader.getAttributeValue(null, "name")
        val title       = reader.getAttributeValue(null, "title")
        val hreflang    = reader.getAttributeValue(null, "hreflang")
        val profile     = reader.getAttributeValue(null, "profile")
        val deprecation = reader.getAttributeValue(null, "deprecation")

        // consume to END_ELEMENT
        while (reader.hasNext()) {
            val evt = reader.next()
            if (evt == EventType.END_ELEMENT) break
        }

        return rel to HalLink(
            href = href, templated = templated, type = type,
            name = name, title = title, hreflang = hreflang,
            profile = profile, deprecation = deprecation,
        )
    }

    private fun collectText(reader: nl.adaptivity.xmlutil.XmlReader): String {
        val sb = StringBuilder()
        var depth = 1
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.TEXT, EventType.CDSECT -> sb.append(reader.text)
                EventType.START_ELEMENT           -> depth++
                EventType.END_ELEMENT             -> { depth--; if (depth == 0) break }
                else                              -> {}
            }
        }
        return sb.toString().trim()
    }
}
