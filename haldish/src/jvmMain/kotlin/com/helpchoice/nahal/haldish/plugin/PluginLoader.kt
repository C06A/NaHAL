package com.helpchoice.nahal.haldish.plugin

import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

/**
 * JVM plugin discovery:
 * 1. If the `HALDISH_PLUGIN_PATH` environment variable is set, load the JAR at that path
 *    and search it for a [HaldishPlugin] via `ServiceLoader`.
 * 2. Otherwise search the thread-context classloader via `ServiceLoader`.
 * 3. If nothing is found, return [NoOpPlugin].
 *
 * **Plugin JAR contract:** the JAR must contain
 * `META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`
 * with the fully-qualified name of the implementing class on its first non-blank line.
 */
internal actual fun loadPlugin(): HaldishPlugin {
    val envPath = System.getenv("HALDISH_PLUGIN_PATH")
    if (envPath != null) {
        val file = File(envPath)
        if (file.exists() && file.isFile) {
            val loader = URLClassLoader(
                arrayOf(file.toURI().toURL()),
                Thread.currentThread().contextClassLoader,
            )
            ServiceLoader.load(HaldishPlugin::class.java, loader).firstOrNull()
                ?.let { return it }
        }
    }
    return ServiceLoader.load(
        HaldishPlugin::class.java,
        Thread.currentThread().contextClassLoader,
    ).firstOrNull() ?: NoOpPlugin
}

internal actual fun platformPluginConfig(): HaldishPluginConfig =
    HaldishPluginConfig(platform = "jvm", version = HALDISH_VERSION)
