package com.helpchoice.nahal.haldish.plugin

/**
 * JS plugin discovery: checks `window.__haldishPlugin` for a plugin object.
 * If the global is present, wraps it in [JsPluginAdapter]; otherwise returns [NoOpPlugin].
 *
 * **Plugin JS contract:** set the global **before** the Kotlin module initialises:
 * ```javascript
 * window.__haldishPlugin = {
 *     initialize:   function(platform, version) { … },
 *     preRequest:   function(req)               { return req; },
 *     postResponse: function(doc, resp)          { return doc; },
 * };
 * ```
 * Any missing callback is silently ignored. See `PLUGIN_CONTRACT.md` for the
 * exact shapes of `req`, `doc`, and `resp`.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val raw = js("(typeof window !== 'undefined' && window.__haldishPlugin) ? window.__haldishPlugin : null")
    return if (raw != null) JsPluginAdapter(raw) else NoOpPlugin
}

internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "js", version = HALDISH_VERSION)
