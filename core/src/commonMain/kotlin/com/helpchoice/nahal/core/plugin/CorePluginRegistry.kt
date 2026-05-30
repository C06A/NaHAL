package com.helpchoice.nahal.core.plugin

import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

object CorePluginRegistry {
    private val _plugins = mutableMapOf<String, HaldishPlugin>()

    /** Register a plugin by its fully-qualified class name. */
    fun register(fqn: String, plugin: HaldishPlugin) { _plugins[fqn] = plugin }
    fun clear() { _plugins.clear() }
    internal fun find(fqn: String): HaldishPlugin? = _plugins[fqn]
}
