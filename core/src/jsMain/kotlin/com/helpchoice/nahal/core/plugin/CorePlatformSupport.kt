package com.helpchoice.nahal.core.plugin

import com.charleskorn.kaml.*
import com.helpchoice.nahal.core.PluginConfigException
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

internal actual fun coreEnvVar(name: String): String? =
    js("(typeof process !== 'undefined') ? (process.env[name] || null) : null") as? String

// eval('require') keeps webpack from statically resolving 'fs', which breaks browser bundles;
// the Node check makes the browser path fail loud instead of at the require call.
internal actual fun coreReadTextFile(path: String): String =
    js(
        """(function() {
            if (typeof process === 'undefined' || !process.versions || !process.versions.node)
                throw Error('coreReadTextFile requires Node.js (no filesystem in the browser)');
            return eval('require')('fs').readFileSync(path, 'utf8');
        })()"""
    ) as String

internal actual fun coreParseYaml(content: String): Map<String, Any?> {
    val root = try { Yaml.default.parseToYamlNode(content) }
               catch (e: Exception) { throw PluginConfigException("Invalid YAML config: ${e.message}", e) }
    if (root !is YamlMap) throw PluginConfigException("Config file root must be a YAML mapping")
    return root.toPlainMap()
}

private fun YamlMap.toPlainMap(): Map<String, Any?> =
    entries.entries.associate { (k, v) -> k.content to v.toPlainAny() }

private fun YamlNode.toPlainAny(): Any? = when (this) {
    is YamlNull       -> null
    is YamlScalar     -> when (content.lowercase()) {
        "true"        -> true
        "false"       -> false
        "null", "~"   -> null
        else          -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }
    is YamlList       -> items.map { it.toPlainAny() }
    is YamlMap        -> toPlainMap()
    is YamlTaggedNode -> innerNode.toPlainAny()
    else              -> toString()
}

internal actual fun coreInstantiatePlugin(fqn: String): HaldishPlugin? =
    CorePluginRegistry.find(fqn)

internal actual fun corePlatformName(): String = "js"

internal actual fun coreBrowserRawConfig(): Map<String, Any?>? {
    val raw = js("(typeof window !== 'undefined' && window.__nahalConfig) ? window.__nahalConfig : null")
    return if (raw != null) jsObjectToMap(raw) else null
}

private fun jsObjectToMap(obj: dynamic): Map<String, Any?> {
    val keys = js("Object.keys(obj)") as Array<String>
    return keys.associate { key ->
        val value = obj[key]
        key to jsValueToAny(value)
    }
}

private fun jsValueToAny(value: dynamic): Any? = when {
    value == null || value == undefined -> null
    js("typeof value === 'object' && !Array.isArray(value)") as Boolean -> jsObjectToMap(value)
    js("Array.isArray(value)") as Boolean -> {
        val len = value.length as Int
        (0 until len).map { jsValueToAny(value[it]) }
    }
    else -> value as? Any
}
