@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.chain

import com.helpchoice.nahal.core.plugin.CorePluginRegistry
import com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin
import com.helpchoice.nahal.plugin.curie.CuriePlugin
import com.helpchoice.nahal.plugin.logger.LoggerPlugin
import com.helpchoice.nahal.ui.main as launchUi
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.setenv

/**
 * macOS entry point that launches the NaHAL UI with the curie → base-url-rewriter → logger
 * plugins chained — the native counterpart of this module's `jvmRun`.
 *
 * Native has no reflection, so :core's config-driven loader resolves plugin FQNs through
 * [CorePluginRegistry]. We register the three plugins, write a JSON config listing them in the
 * same order jvmRun uses (curie first, logger last), point `HALDISH_CONFIG` at it, then hand off
 * to the UI. All of this runs before the UI constructs its HalNavigator, which is what reads the
 * config.
 */
fun main() {
    val logDir = NSTemporaryDirectory() + "haldish-log"
    CorePluginRegistry.register(
        "com.helpchoice.nahal.plugin.curie.CuriePlugin", CuriePlugin(),
    )
    CorePluginRegistry.register(
        "com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin", BaseUrlRewriterPlugin(),
    )
    CorePluginRegistry.register(
        "com.helpchoice.nahal.plugin.logger.LoggerPlugin", LoggerPlugin(directory = logDir),
    )

    val configPath = NSTemporaryDirectory() + "nahal-chain-config.json"
    writeTextFile(
        configPath,
        """
        {
          "com.helpchoice.nahal.plugin.curie.CuriePlugin": {},
          "com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin": {},
          "com.helpchoice.nahal.plugin.logger.LoggerPlugin": {}
        }
        """.trimIndent(),
    )
    setenv("HALDISH_CONFIG", configPath, 1)

    launchUi()
}

private fun writeTextFile(path: String, content: String) {
    val fp = fopen(path, "w") ?: return
    fputs(content, fp)
    fclose(fp)
}
