package com.helpchoice.nahal.haldish.plugin

/**
 * Platform-specific plugin discovery.
 * Each platform provides an `actual` implementation; returns [NoOpPlugin] if nothing is found.
 */
internal expect fun loadPlugin(): HaldishPlugin

/**
 * Platform-specific config for the startup [HaldishPluginConfig].
 * Each platform provides the correct `platform` string and library version.
 */
internal expect fun platformPluginConfig(): HaldishPluginConfig
