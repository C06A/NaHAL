package com.helpchoice.nahal.core.plugin

import com.helpchoice.nahal.core.PluginConfigException
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

internal actual fun coreEnvVar(name: String): String? = null

internal actual fun coreReadTextFile(path: String): String =
    throw PluginConfigException("File reading not supported on WasmJS; use window.__nahalConfig")

internal actual fun coreParseYaml(content: String): Map<String, Any?> =
    throw PluginConfigException("YAML not supported on WasmJS; use window.__nahalConfig")

internal actual fun coreInstantiatePlugin(fqn: String): HaldishPlugin? =
    CorePluginRegistry.find(fqn)

internal actual fun corePlatformName(): String = "wasmjs"

internal actual fun coreBrowserRawConfig(): Map<String, Any?>? {
    val raw = wasmGetNamedConfig() ?: return null
    return wasmJsObjectToMap(raw)
}

private fun wasmJsObjectToMap(obj: JsAny): Map<String, Any?> {
    val keys = wasmObjectKeys(obj) ?: return emptyMap()
    val count = wasmArrayLength(keys)
    return (0 until count).associate { i ->
        val key = wasmArrayGetString(keys, i) ?: ""
        val value = wasmGetAny(obj, key)
        key to if (value != null) wasmValueToAny(value) else null
    }
}

private fun wasmValueToAny(value: JsAny): Any? =
    if (wasmIsObject(value)) wasmJsObjectToMap(value)
    else if (wasmIsArray(value)) {
        val count = wasmArrayLength(value)
        (0 until count).map { i -> wasmArrayGetAny(value, i)?.let { wasmValueToAny(it) } }
    }
    else wasmToString(value)

// Single-expression top-level helpers (Kotlin/Wasm requirement)

private fun wasmGetNamedConfig(): JsAny? =
    js("(typeof window !== 'undefined' && window.__nahalConfig) ? window.__nahalConfig : null")

private fun wasmObjectKeys(obj: JsAny): JsAny? =
    js("(obj != null && typeof obj === 'object') ? Object.keys(obj) : null")

private fun wasmArrayLength(arr: JsAny): Int = js("arr.length | 0")

private fun wasmArrayGetString(arr: JsAny, i: Int): String? =
    js("(typeof arr[i] === 'string') ? arr[i] : null")

private fun wasmGetAny(obj: JsAny, key: String): JsAny? =
    js("(obj[key] != null && obj[key] !== undefined) ? obj[key] : null")

private fun wasmArrayGetAny(arr: JsAny, i: Int): JsAny? =
    js("(arr[i] != null && arr[i] !== undefined) ? arr[i] : null")

private fun wasmIsObject(value: JsAny): Boolean =
    js("typeof value === 'object' && !Array.isArray(value) && value !== null")

private fun wasmIsArray(value: JsAny): Boolean = js("Array.isArray(value)")

private fun wasmToString(value: JsAny): String = js("String(value)")
