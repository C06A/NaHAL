package com.helpchoice.nahal.haldish.plugin

/**
 * WasmJS plugin discovery: checks `window.__haldishPlugin` for a plugin object.
 * If the global is present, wraps it in [WasmPluginAdapter]; otherwise returns [NoOpPlugin].
 *
 * Uses the same `window.__haldishPlugin` contract as the JS target.
 * See `PLUGIN_CONTRACT.md` for the full specification.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    val raw = getHaldishPluginGlobal()
    return if (raw != null) WasmPluginAdapter(raw) else NoOpPlugin
}

internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "wasmjs", version = HALDISH_VERSION)

/** Reads `window.__haldishPlugin`; returns `null` if the global is absent or undefined. */
private fun getHaldishPluginGlobal(): JsAny? = js(
    "(typeof window !== 'undefined' && window.__haldishPlugin) ? window.__haldishPlugin : null"
)
