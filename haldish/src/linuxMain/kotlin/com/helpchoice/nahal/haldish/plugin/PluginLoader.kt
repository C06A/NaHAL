@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import kotlinx.cinterop.toKString
import platform.posix.RTLD_NOW
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.getenv

/**
 * Linux plugin discovery:
 * 1. [NativePluginRegistrar] — C callers registered function pointers via `haldish_plugin_register()`.
 * 2. `HALDISH_PLUGIN_PATH` env var → `dlopen` that path.
 * 3. `dlopen("libhaldish_plugin.so", RTLD_NOW)` from default `LD_LIBRARY_PATH`.
 * 4. [NoOpPlugin] if nothing is found.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    // 1. Direct function-pointer registration
    NativePluginRegistrar.getPlugin()?.let { return it }

    // 2. Env-var path or default name
    val libPath = getenv("HALDISH_PLUGIN_PATH")?.toKString() ?: "libhaldish_plugin.so"
    val handle  = dlopen(libPath, RTLD_NOW)
    if (handle != null) {
        val adapter = NativeDylibPluginAdapter.from(handle)
        if (adapter != null) return adapter
        dlclose(handle)
    }

    return NoOpPlugin
}

internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "linux", version = HALDISH_VERSION)
