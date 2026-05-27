@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import com.helpchoice.nahal.ui.component.LocalFilePicker
import com.helpchoice.nahal.ui.component.PickedFile
import kotlinx.cinterop.*
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.Foundation.NSNotification
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.posix.*

fun main() {
    val app = NSApplication.sharedApplication()
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)

    val delegate = object : NSObject(), NSApplicationDelegateProtocol {
        override fun applicationDidFinishLaunching(notification: NSNotification) {
            Window(
                title = "NaHAL",
                size = DpSize(1280.dp, 820.dp),
            ) {
                CompositionLocalProvider(
                    LocalFilePicker provides { callback -> pickFile(callback) }
                ) {
                    NaHalNavigator()
                }
            }
            app.activateIgnoringOtherApps(true)
        }

        override fun applicationShouldTerminateAfterLastWindowClosed(sender: NSApplication): Boolean = true
    }
    app.delegate = delegate
    app.run()
}

@Suppress("UNCHECKED_CAST")
private fun pickFile(callback: (PickedFile?) -> Unit) {
    val panel = NSOpenPanel()
    panel.canChooseFiles = true
    panel.canChooseDirectories = false
    panel.allowsMultipleSelection = false
    if (panel.runModal() != NSModalResponseOK) {
        callback(null)
        return
    }
    val url = panel.URLs.firstOrNull() as? NSURL
    val path = url?.path
    if (url == null || path == null) {
        callback(null)
        return
    }
    val content = readTextFile(path)
    callback(
        if (content != null)
            PickedFile(url.lastPathComponent ?: path.substringAfterLast('/'), content)
        else null
    )
}

private fun readTextFile(path: String): String? {
    val file = fopen(path, "r") ?: return null
    val sb = StringBuilder()
    val buf = ByteArray(8192)
    try {
        while (true) {
            val n = buf.usePinned { fread(it.addressOf(0), 1.convert(), buf.size.convert(), file).toInt() }
            if (n <= 0) break
            sb.append(buf.decodeToString(0, n))
        }
    } finally {
        fclose(file)
    }
    return sb.toString()
}
