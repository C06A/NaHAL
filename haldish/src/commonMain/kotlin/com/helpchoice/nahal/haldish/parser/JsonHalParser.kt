package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.HalParseException
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlinx.serialization.json.*

internal object JsonHalParser {

    fun parse(body: String): HalDocument {
        val root: JsonElement = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw HalParseException("Invalid JSON: ${e.message}", e)
        }
        if (root is JsonArray) {
            val items = root.map { el ->
                val obj = el as? JsonObject ?: throw HalParseException("HAL array element must be an object")
                parseObject(obj)
            }
            return HalDocument(rawBody = body, items = items)
        }
        if (root !is JsonObject) throw HalParseException("HAL JSON root must be an object")
        return parseObject(root)
    }

    private fun parseObject(obj: JsonObject): HalDocument {
        val links    = parseLinks(obj["_links"])
        val embedded = parseEmbedded(obj["_embedded"])
        val props    = obj.filterKeys { it != "_links" && it != "_embedded" }
        return HalDocument(links = links, embedded = embedded, properties = props)
    }

    private fun parseLinks(el: JsonElement?): Map<String, List<HalLink>> {
        if (el == null || el is JsonNull) return emptyMap()
        val obj = el as? JsonObject
            ?: throw HalParseException("_links must be a JSON object")
        return obj.mapValues { (_, v) ->
            when (v) {
                is JsonArray  -> v.map { parseLinkObject(it as? JsonObject
                    ?: throw HalParseException("Link array element must be an object")) }
                is JsonObject -> listOf(parseLinkObject(v))
                else          -> throw HalParseException("Unexpected _links value type for key")
            }
        }
    }

    private fun parseLinkObject(obj: JsonObject): HalLink = HalLink(
        href        = obj["href"]?.jsonPrimitive?.content
                          ?: throw HalParseException("HAL link missing required 'href'"),
        templated   = obj["templated"]?.jsonPrimitive?.booleanOrNull ?: false,
        type        = obj["type"]?.jsonPrimitive?.contentOrNull,
        name        = obj["name"]?.jsonPrimitive?.contentOrNull,
        title       = obj["title"]?.jsonPrimitive?.contentOrNull,
        hreflang    = obj["hreflang"]?.jsonPrimitive?.contentOrNull,
        profile     = obj["profile"]?.jsonPrimitive?.contentOrNull,
        deprecation = obj["deprecation"]?.jsonPrimitive?.contentOrNull,
    )

    private fun parseEmbedded(el: JsonElement?): Map<String, List<HalDocument>> {
        if (el == null || el is JsonNull) return emptyMap()
        val obj = el as? JsonObject
            ?: throw HalParseException("_embedded must be a JSON object")
        return obj.mapValues { (_, v) ->
            when (v) {
                is JsonArray  -> v.map { parseObject(it as? JsonObject
                    ?: throw HalParseException("Embedded array element must be an object")) }
                is JsonObject -> listOf(parseObject(v))
                else          -> throw HalParseException("Unexpected _embedded value type")
            }
        }
    }
}
