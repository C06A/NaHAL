@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.windows.FreeLibrary
import platform.windows.LoadLibraryA

/**
 * Windows plugin discovery:
 * 1. [NativePluginRegistrar] — C callers registered function pointers via `haldish_plugin_register()`.
 * 2. `HALDISH_PLUGIN_PATH` env var → `LoadLibraryA` that path.
 * 3. `LoadLibraryA("haldish_plugin.dll")` from default DLL search path.
 * 4. [NoOpPlugin] if nothing is found.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    // 1. Direct function-pointer registration
    NativePluginRegistrar.getPlugin()?.let { return it }

    // 2. Env-var path or default name
    val libPath = getenv("HALDISH_PLUGIN_PATH")?.toKString() ?: "haldish_plugin.dll"
    val handle  = LoadLibraryA(libPath)
    if (handle != null) {
        val adapter = NativeDylibPluginAdapter.from(handle)
        if (adapter != null) return adapter
        FreeLibrary(handle)
    }

    return NoOpPlugin
}

internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "windows", version = HALDISH_VERSION)
