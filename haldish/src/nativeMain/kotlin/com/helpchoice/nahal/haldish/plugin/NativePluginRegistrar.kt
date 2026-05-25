package com.helpchoice.nahal.haldish.plugin

/**
 * In-process plugin registration for C API callers.
 *
 * C callers may use `haldish_plugin_register(…)` (declared in [NativeCApi]) to
 * register C function-pointer callbacks directly — without needing a separate
 * dynamic library.  This registrar holds that plugin so the platform loaders
 * (linuxMain, mingwMain, appleMain) can retrieve it.
 *
 * Must be called **before** the first HTTP operation (the client initialises lazily).
 */
internal object NativePluginRegistrar {
    private var plugin: HaldishPlugin? = null

    fun setPlugin(p: HaldishPlugin) { plugin = p }
    fun getPlugin(): HaldishPlugin? = plugin
    fun clear() { plugin = null }
}
