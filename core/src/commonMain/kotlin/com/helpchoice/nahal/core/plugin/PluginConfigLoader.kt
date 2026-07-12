package com.helpchoice.nahal.core.plugin

import com.helpchoice.nahal.core.PluginConfigException
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlinx.serialization.json.*

internal const val CORE_VERSION = "0.1.0-SNAPSHOT"

/**
 * Loads plugins from the config source and returns a single [HaldishPlugin] ready for injection
 * into [com.helpchoice.nahal.haldish.http.HalHttpClient].
 *
 * Resolution order:
 * 1. [coreBrowserRawConfig] — browser JS / WasmJS (window.__nahalConfig)
 * 2. `HALDISH_CONFIG` env var → read file, parse JSON or YAML
 * 3. Neither present → [CoreNoOpPlugin] (no plugins)
 */
internal fun buildConfiguredPlugin(): HaldishPlugin {
    val rawMap = coreBrowserRawConfig()
        ?: run {
            val path = coreEnvVar("HALDISH_CONFIG") ?: return CoreNoOpPlugin
            val content = coreReadTextFile(path)
            val ext = path.substringAfterLast('.').lowercase()
            when (ext) {
                "yaml", "yml" -> coreParseYaml(content)
                else -> parseJsonToMap(content)
            }
        }

    val plugins = mutableListOf<HaldishPlugin>()
    walkTree(rawMap, "", plugins)

    return when (plugins.size) {
        0 -> CoreNoOpPlugin
        1 -> plugins[0]
        else -> ChainedPlugin(plugins)
    }
}

private fun walkTree(node: Map<String, Any?>, path: String, acc: MutableList<HaldishPlugin>) {
    for ((key, value) in node) {
        val fqn = if (path.isEmpty()) key else "$path.$key"
        val plugin = coreInstantiatePlugin(fqn)
        when {
            plugin != null -> {
                val properties = (value as? Map<*, *>)
                    ?.entries
                    ?.associate { (k, v) -> k.toString() to v }
                    ?: emptyMap()
                plugin.initialize(HaldishPluginConfig(corePlatformName(), CORE_VERSION, properties))
                acc += plugin
            }
            value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                walkTree(value as Map<String, Any?>, fqn, acc)
            }
            else -> throw PluginConfigException(
                "Unexpected scalar at '$fqn' — check for a typo in a plugin FQN"
            )
        }
    }
}

private fun parseJsonToMap(content: String): Map<String, Any?> {
    val element = try {
        Json.parseToJsonElement(content)
    } catch (e: Exception) {
        throw PluginConfigException("Invalid JSON config: ${e.message}", e)
    }
    if (element !is JsonObject)
        throw PluginConfigException("Config file root must be a JSON object")
    return element.toPlainMap()
}

private fun JsonElement.toPlainMap(): Map<String, Any?> {
    require(this is JsonObject) { "Expected JSON object" }
    return (this as JsonObject).entries.associate { (k, v) -> k to v.toPlainAny() }
}

private fun JsonElement.toPlainAny(): Any? = when (this) {
    is JsonNull      -> null
    is JsonPrimitive -> when {
        isString            -> content
        content == "true"   -> true
        content == "false"  -> false
        else                -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }
    is JsonObject    -> toPlainMap()
    is JsonArray     -> map { it.toPlainAny() }
}

private object CoreNoOpPlugin : HaldishPlugin

private class ChainedPlugin(private val plugins: List<HaldishPlugin>) : HaldishPlugin {

    override fun preLink(
        link: HalLink,
        path: ResourcePath,
        rootDocument: HalDocument,
    ): HalLink = plugins.fold(link) { acc, p -> p.preLink(acc, path, rootDocument) }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest =
        plugins.fold(request) { acc, p -> p.preRequest(acc) }

    override fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument =
        plugins.fold(document) { acc, p -> p.postResponse(acc, response) }
}
