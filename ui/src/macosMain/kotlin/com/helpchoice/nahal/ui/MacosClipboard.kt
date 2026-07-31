@file:Suppress("DEPRECATION") // LocalClipboardManager path kept for older Compose copy handlers
@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.helpchoice.nahal.ui

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.text.AnnotatedString
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString

// Compose's own macOS clipboard (NSPasteboardPlatformClipboard) calls `setString:forType:`
// WITHOUT a preceding `clearContents()`, so AppKit rejects the write (the type was never declared),
// and its `ClipEntry.clipMetadata` is a `TODO(...)`. On Kotlin/Native an exception thrown on the UI
// thread unwinds through an Obj-C callback and aborts the whole process — so a stray clipboard
// failure crashes the app (e.g. Cmd+C over a SelectionContainer). These replacements talk to
// NSPasteboard directly, declare ownership before writing, only ever touch plain text (never the
// unimplemented clipMetadata), and wrap every native call so a failure degrades to a no-op copy
// instead of a crash.

private val pasteboard: NSPasteboard get() = NSPasteboard.generalPasteboard

private fun readPlainText(): String? =
    runCatching { pasteboard.stringForType(NSPasteboardTypeString) }.getOrNull()

private fun writePlainText(text: String?) {
    runCatching {
        if (text == null) {
            pasteboard.clearContents()
        } else {
            // clearContents() declares ownership of the string type; setString fails silently
            // without it.
            pasteboard.clearContents()
            pasteboard.setString(text, forType = NSPasteboardTypeString)
        }
    }
}

/** Suspend clipboard used by selection/text-field copy in Compose 1.8 (`LocalClipboard`). */
internal object NahalMacosClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? =
        readPlainText()?.takeIf { it.isNotEmpty() }?.let { ClipEntry.withPlainText(it) }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = writePlainText(clipEntry?.getPlainText())

    override val nativeClipboard: NativeClipboard get() = pasteboard
}

/** Legacy clipboard facade still consulted on some paths (`LocalClipboardManager`). */
@Suppress("DEPRECATION")
internal object NahalMacosClipboardManager : ClipboardManager {
    override fun setText(annotatedString: AnnotatedString) = writePlainText(annotatedString.text)

    override fun getText(): AnnotatedString? = readPlainText()?.let { AnnotatedString(it) }

    override fun hasText(): Boolean = !readPlainText().isNullOrEmpty()

    override fun getClip(): ClipEntry? =
        readPlainText()?.takeIf { it.isNotEmpty() }?.let { ClipEntry.withPlainText(it) }

    override fun setClip(clipEntry: ClipEntry?) = writePlainText(clipEntry?.getPlainText())

    override val nativeClipboard: NativeClipboard get() = pasteboard
}
