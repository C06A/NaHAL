@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native (Linux / macOS / Windows via MinGW) implementation using POSIX file APIs.
 * Covers all targets in `nativeMain` (linuxMain, macosMain, mingwMain, iosMain).
 */

/**
 * Creates a single directory. Errors are ignored — EEXIST is the expected case here.
 *
 * Per-platform because MinGW's `mkdir` takes no mode argument, so the commonizer produces
 * no shared declaration for it across the POSIX and Windows targets.
 */
internal expect fun platformMkdirOne(path: String)

internal actual fun platformMakeDir(path: String) {
    // Create each directory component, ignoring EEXIST at each step.
    val parts = path.replace('\\', '/').split('/')
    val sb = StringBuilder()
    for (part in parts) {
        if (part.isEmpty()) { sb.append('/'); continue }
        if (sb.isNotEmpty() && sb.last() != '/') sb.append('/')
        sb.append(part)
        platformMkdirOne(sb.toString())
    }
}

internal actual fun platformWriteFile(filePath: String, content: String) {
    val fp = fopen(filePath, "w") ?: return
    fputs(content, fp)
    fclose(fp)
}

internal actual fun platformCurrentTimestamp(): String = memScoped {
    val t  = alloc<time_tVar>()
    time(t.ptr)
    val tm = localtime(t.ptr)?.pointed ?: return@memScoped "19700101T000000"
    val buf = allocArray<ByteVar>(16)
    strftime(buf, 16u, "%Y%m%dT%H%M%S", tm.ptr)
    buf.toKString()
}
