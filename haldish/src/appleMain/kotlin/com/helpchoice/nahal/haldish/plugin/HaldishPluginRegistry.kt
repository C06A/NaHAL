package com.helpchoice.nahal.haldish.plugin

/**
 * Static plugin registration for iOS and macOS Swift/ObjC callers.
 *
 * Call [setPlugin] once at application startup (e.g. in `AppDelegate.application(_:didFinishLaunchingWithOptions:)`)
 * before any HTTP request is made:
 *
 * ```swift
 * // Swift
 * HaldishPluginRegistry.shared.setPlugin(MyAuthPlugin())
 * ```
 *
 * If no plugin is registered the library operates normally with a no-op plugin.
 * Only one plugin can be registered at a time; subsequent calls replace the previous one.
 */
object HaldishPluginRegistry {
    private var plugin: HaldishPlugin? = null

    /** Register [plugin] as the active plugin. Must be called before the first HTTP request. */
    fun setPlugin(plugin: HaldishPlugin) {
        this.plugin = plugin
    }

    /** Remove the currently registered plugin, reverting to no-op behaviour. */
    fun clearPlugin() {
        plugin = null
    }

    /** Returns the registered plugin, or `null` if none has been registered. */
    internal fun getPlugin(): HaldishPlugin? = plugin
}
