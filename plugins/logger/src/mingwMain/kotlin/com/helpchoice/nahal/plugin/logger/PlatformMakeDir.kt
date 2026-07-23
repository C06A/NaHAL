@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import platform.posix.mkdir

/** Windows (MinGW) directory creation using the 1-arg CRT `mkdir(path)` (no mode bits). */
internal actual fun platformMakeDir(path: String) {
    // Create each directory component, ignoring EEXIST at each step.
    val parts = path.replace('\\', '/').split('/')
    val sb = StringBuilder()
    for (part in parts) {
        if (part.isEmpty()) { sb.append('/'); continue }
        if (sb.isNotEmpty() && sb.last() != '/') sb.append('/')
        sb.append(part)
        mkdir(sb.toString())
        // Ignoring errors: EEXIST is expected for existing dirs; propagate nothing.
    }
}
