package com.helpchoice.nahal.haldish.parser

import com.charleskorn.kaml.*
import com.helpchoice.nahal.haldish.HalParseException
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlinx.serialization.json.*

internal actual object YamlHalParser {

    actual fun parse(body: String): HalDocument {
        val root = try {
            Yaml.default.parseToYamlNode(body)
        } catch (e: Exception) {
            throw HalParseException("Invalid YAML: ${e.message}", e)
        }
        if (root !is YamlMap) throw HalParseException("HAL YAML root must be a mapping")
        return parseMap(root)
    }

    private fun parseMap(map: YamlMap): HalDocument {
        val links    = parseLinks(map.get("_links") as? YamlMap)
        val embedded = parseEmbedded(map.get("_embedded") as? YamlMap)
        val props    = buildMap<String, JsonElement> {
            map.entries.forEach { (key, value) ->
                if (key.content != "_links" && key.content != "_embedded")
                    put(key.content, nodeToJsonElement(value))
            }
        }
        return HalDocument(links = links, embedded = embedded, properties = props)
    }

    private fun parseLinks(linksMap: YamlMap?): Map<String, List<HalLink>> {
        if (linksMap == null) return emptyMap()
        return buildMap {
            linksMap.entries.forEach { (key, value) ->
                val rel = key.content
                put(rel, when (value) {
                    is YamlList -> value.items.map { parseLinkNode(it as? YamlMap
                        ?: throw HalParseException("Link list item must be a YAML mapping")) }
                    is YamlMap  -> listOf(parseLinkNode(value))
                    else        -> throw HalParseException("Unexpected YAML link value for '$rel'")
                })
            }
        }
    }

    private fun parseLinkNode(map: YamlMap): HalLink = HalLink(
        href        = (map.get("href") as? YamlScalar)?.content
                          ?: throw HalParseException("HAL YAML link missing required 'href'"),
        templated   = (map.get("templated") as? YamlScalar)?.content?.lowercase() == "true",
        type        = (map.get("type") as? YamlScalar)?.content,
        name        = (map.get("name") as? YamlScalar)?.content,
        title       = (map.get("title") as? YamlScalar)?.content,
        hreflang    = (map.get("hreflang") as? YamlScalar)?.content,
        profile     = (map.get("profile") as? YamlScalar)?.content,
        deprecation = (map.get("deprecation") as? YamlScalar)?.content,
    )

    private fun parseEmbedded(embeddedMap: YamlMap?): Map<String, List<HalDocument>> {
        if (embeddedMap == null) return emptyMap()
        return buildMap {
            embeddedMap.entries.forEach { (key, value) ->
                val rel = key.content
                put(rel, when (value) {
                    is YamlList -> value.items.map { parseMap(it as? YamlMap
                        ?: throw HalParseException("Embedded list item must be a YAML mapping")) }
                    is YamlMap  -> listOf(parseMap(value))
                    else        -> throw HalParseException("Unexpected YAML embedded value for '$rel'")
                })
            }
        }
    }

    private fun nodeToJsonElement(node: YamlNode): JsonElement = when (node) {
        is YamlNull   -> JsonNull
        is YamlScalar -> when (node.content.lowercase()) {
            "true"      -> JsonPrimitive(true)
            "false"     -> JsonPrimitive(false)
            "null", "~" -> JsonNull
            else        -> node.content.toLongOrNull()?.let { JsonPrimitive(it) }
                        ?: node.content.toDoubleOrNull()?.let { JsonPrimitive(it) }
                        ?: JsonPrimitive(node.content)
        }
        is YamlList   -> JsonArray(node.items.map { nodeToJsonElement(it) })
        is YamlMap    -> JsonObject(buildMap {
            node.entries.forEach { (key, value) -> put(key.content, nodeToJsonElement(value)) }
        })
        else          -> JsonPrimitive(node.toString())
    }
}
