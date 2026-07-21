@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import platform.posix.mkdir

// Windows has no POSIX mode argument — permissions come from the parent directory's ACL.
internal actual fun platformMkdirOne(path: String) {
    mkdir(path)
}
