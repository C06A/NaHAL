package com.helpchoice.nahal.testkit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Coerces a kotlinx-serialization [JsonElement] (how `:haldish` exposes HAL properties) into a
 * plain Kotlin value, so property extraction reads naturally from both Kotlin and Groovy:
 * `null`, [Boolean], [Long]/[Double], [String], [List], or [Map] of the same.
 */
internal object JsonCoerce {

    fun value(element: JsonElement?): Any? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive  -> primitive(element)
        is JsonArray      -> element.map { value(it) }
        is JsonObject     -> element.mapValues { value(it.value) }
    }

    private fun primitive(p: JsonPrimitive): Any? {
        if (p.isString) return p.content
        p.booleanOrNull?.let { return it }
        p.longOrNull?.let { return it }
        p.doubleOrNull?.let { return it }
        return p.content
    }
}
