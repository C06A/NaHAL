@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import kotlinx.cinterop.toKString
import platform.posix.RTLD_NOW
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.getenv

/**
 * Apple (iOS + macOS) plugin discovery:
 * 1. [HaldishPluginRegistry] — Swift/ObjC callers register a plugin at app startup.
 * 2. `HALDISH_PLUGIN_PATH` env var → `dlopen` that path (macOS only; fails silently on iOS).
 * 3. `dlopen("libhaldish_plugin.dylib", RTLD_NOW)` from the default dylib search path.
 * 4. [NoOpPlugin] if nothing is found.
 *
 * **iOS note:** Dynamic library loading via `dlopen` is restricted by the OS on iOS.
 * If the env-var or lib-path path is not a system-signed library the `dlopen` call
 * returns `null` and the loader moves on to the next option automatically.
 *
 * On macOS the library is looked up in `DYLD_LIBRARY_PATH` / `@rpath` / standard
 * system locations, matching normal macOS dynamic linking conventions.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    // 1. Static registration (works on both iOS and macOS)
    HaldishPluginRegistry.getPlugin()?.let { return it }

    // 2 & 3. Try dlopen (macOS succeeds; iOS will return null — graceful fallback)
    val libPath = getenv("HALDISH_PLUGIN_PATH")?.toKString() ?: "libhaldish_plugin.dylib"

    val handle = dlopen(libPath, RTLD_NOW)
    if (handle != null) {
        val adapter = NativeDylibPluginAdapter.from(handle)
        if (adapter != null) return adapter
        dlclose(handle)
    }

    return NoOpPlugin
}

/**
 * Platform string is `"apple"` for all Apple targets (iOS + macOS) from this source set.
 * If you need distinct `"ios"` / `"macos"` strings, split `appleMain` into separate
 * `iosMain` and `macosMain` source sets.
 */
internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "apple", version = HALDISH_VERSION)
