@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import com.helpchoice.nahal.ui.component.ExternalBody
import com.helpchoice.nahal.ui.component.ExternalOpener
import com.helpchoice.nahal.ui.component.LocalExternalOpener
import com.helpchoice.nahal.ui.component.LocalFilePicker
import com.helpchoice.nahal.ui.component.PickedFile
import kotlinx.cinterop.*
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.AppKit.NSWorkspace
import platform.Foundation.NSDate
import platform.Foundation.NSNotification
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UniformTypeIdentifiers.UTType
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
                    LocalFilePicker provides { callback -> pickFile(callback) },
                    LocalExternalOpener provides ExternalOpener(
                        appNameFor = ::defaultAppName,
                        open = ::openWithDefaultApp,
                    ),
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

/** Writes the fetched body to a temp file and opens it with the OS default application. */
private fun openWithDefaultApp(body: ExternalBody): Boolean {
    val stamp = NSDate().timeIntervalSince1970.toLong()
    val path = NSTemporaryDirectory() + "nahal-$stamp.${body.extension}"
    if (!writeBytesFile(path, body.bytes)) return false
    return NSWorkspace.sharedWorkspace.openURL(NSURL.fileURLWithPath(path))
}

/** Display name of the default app for [contentType], or null when unknowable. */
private fun defaultAppName(contentType: String?): String? = runCatching {
    val mime = contentType?.substringBefore(';')?.trim() ?: return null
    val type = UTType.typeWithMIMEType(mime) ?: return null
    // NSWorkspace's binding sees UTType only as a forward declaration — cast across the two views.
    @Suppress("CAST_NEVER_SUCCEEDS")
    val appUrl = NSWorkspace.sharedWorkspace
        .URLForApplicationToOpenContentType(type as objcnames.classes.UTType) ?: return null
    appUrl.lastPathComponent?.removeSuffix(".app")
}.getOrNull()

private fun writeBytesFile(path: String, bytes: ByteArray): Boolean {
    val file = fopen(path, "wb") ?: return false
    return try {
        bytes.isEmpty() ||
            bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() } == bytes.size
    } finally {
        fclose(file)
    }
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
