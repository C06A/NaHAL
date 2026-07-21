@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import kotlinx.cinterop.convert
import platform.posix.*

internal actual fun platformMkdirOne(path: String) {
    mkdir(path, (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).convert())
}
