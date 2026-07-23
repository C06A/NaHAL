@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import kotlinx.cinterop.convert
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRWXU
import platform.posix.S_IXGRP
import platform.posix.S_IXOTH
import platform.posix.mkdir

// POSIX directory creation via the 2-arg `mkdir(path, mode)`. Duplicated verbatim
// in linuxMain and appleMain rather than shared: `mode_t` is 32-bit on Linux but
// 16-bit on Darwin, so an intermediate source set spanning both fails the shared
// metadata compile ("numbers with different bit widths"). Keep the two copies in sync.
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
