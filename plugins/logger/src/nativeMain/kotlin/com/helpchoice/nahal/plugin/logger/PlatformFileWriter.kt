@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native (Linux / macOS / Windows via MinGW) implementation using POSIX file APIs.
 * Covers all targets in `nativeMain` (linuxMain, macosMain, mingwMain, iosMain).
 */

internal actual fun platformMakeDir(path: String) {
    // Create each directory component, ignoring EEXIST at each step.
    val parts = path.replace('\\', '/').split('/')
    val sb = StringBuilder()
    for (part in parts) {
        if (part.isEmpty()) { sb.append('/'); continue }
        if (sb.isNotEmpty() && sb.last() != '/') sb.append('/')
        sb.append(part)
        mkdir(sb.toString(), (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).convert())
        // Ignoring errors: EEXIST is expected for existing dirs; propagate nothing.
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
