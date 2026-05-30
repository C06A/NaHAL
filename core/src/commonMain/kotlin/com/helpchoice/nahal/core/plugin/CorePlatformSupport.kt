package com.helpchoice.nahal.core.plugin

import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

internal expect fun coreEnvVar(name: String): String?

/** Read a text file at [path]. Throws [com.helpchoice.nahal.core.PluginConfigException] on I/O failure. */
internal expect fun coreReadTextFile(path: String): String

/** Parse YAML content to a nested map. Throws [com.helpchoice.nahal.core.PluginConfigException] on unsupported platforms. */
internal expect fun coreParseYaml(content: String): Map<String, Any?>

/**
 * Instantiate or locate a [HaldishPlugin] by fully-qualified class name.
 * - JVM: reflection (`Class.forName` + no-arg constructor)
 * - Other platforms: lookup in [CorePluginRegistry]
 * Returns `null` when the name does not resolve to a plugin (treat as package segment).
 * Throws [com.helpchoice.nahal.core.PluginConfigException] on misconfiguration (wrong type, no no-arg ctor).
 */
internal expect fun coreInstantiatePlugin(fqn: String): HaldishPlugin?

internal expect fun corePlatformName(): String

/**
 * Browser JS / WasmJS: return `window.__nahalConfig` as a parsed nested map, or `null` if absent.
 * All non-browser platforms return `null` (use env var + file path instead).
 */
internal expect fun coreBrowserRawConfig(): Map<String, Any?>?
