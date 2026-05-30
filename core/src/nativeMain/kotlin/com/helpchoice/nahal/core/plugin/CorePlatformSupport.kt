@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.core.plugin

import com.helpchoice.nahal.core.PluginConfigException
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import kotlinx.cinterop.*
import platform.posix.*

internal actual fun coreEnvVar(name: String): String? = getenv(name)?.toKString()

internal actual fun coreReadTextFile(path: String): String {
    val file = fopen(path, "r")
        ?: throw PluginConfigException("Cannot open config file: $path")
    return try {
        buildString {
            val buffer = ByteArray(4096)
            buffer.usePinned { pinned ->
                while (true) {
                    val n = fread(pinned.addressOf(0), 1u, 4096u, file).toInt()
                    if (n <= 0) break
                    append(buffer.decodeToString(0, n))
                }
            }
        }
    } finally {
        fclose(file)
    }
}

internal actual fun coreParseYaml(content: String): Map<String, Any?> =
    throw PluginConfigException("YAML config not supported on native; use a JSON config file")

internal actual fun coreInstantiatePlugin(fqn: String): HaldishPlugin? =
    CorePluginRegistry.find(fqn)

internal actual fun corePlatformName(): String = "native"

internal actual fun coreBrowserRawConfig(): Map<String, Any?>? = null
