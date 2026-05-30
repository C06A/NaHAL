package com.helpchoice.nahal.core.plugin

import com.charleskorn.kaml.*
import com.helpchoice.nahal.core.PluginConfigException
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import java.io.File
import java.io.IOException

internal actual fun coreEnvVar(name: String): String? = System.getenv(name)

internal actual fun coreReadTextFile(path: String): String =
    try { File(path).readText() }
    catch (e: IOException) { throw PluginConfigException("Cannot read config file: $path", e) }

internal actual fun coreParseYaml(content: String): Map<String, Any?> {
    val root = try { Yaml.default.parseToYamlNode(content) }
               catch (e: Exception) { throw PluginConfigException("Invalid YAML config: ${e.message}", e) }
    if (root !is YamlMap) throw PluginConfigException("Config file root must be a YAML mapping")
    return root.toPlainMap()
}

private fun YamlMap.toPlainMap(): Map<String, Any?> =
    entries.entries.associate { (k, v) -> k.content to v.toPlainAny() }

private fun YamlNode.toPlainAny(): Any? = when (this) {
    is YamlNull      -> null
    is YamlScalar    -> when (content.lowercase()) {
        "true"       -> true
        "false"      -> false
        "null", "~"  -> null
        else         -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }
    is YamlList      -> items.map { it.toPlainAny() }
    is YamlMap       -> toPlainMap()
    is YamlTaggedNode -> innerNode.toPlainAny()
    else             -> toString()
}

internal actual fun coreInstantiatePlugin(fqn: String): HaldishPlugin? {
    val cls = try { Class.forName(fqn) } catch (_: ClassNotFoundException) { return null }
    if (!HaldishPlugin::class.java.isAssignableFrom(cls))
        throw PluginConfigException("$fqn exists on the classpath but does not implement HaldishPlugin")
    return try {
        cls.getDeclaredConstructor().newInstance() as HaldishPlugin
    } catch (_: NoSuchMethodException) {
        throw PluginConfigException("Plugin $fqn has no no-arg constructor")
    }
}

internal actual fun corePlatformName(): String = "jvm"

internal actual fun coreBrowserRawConfig(): Map<String, Any?>? = null
