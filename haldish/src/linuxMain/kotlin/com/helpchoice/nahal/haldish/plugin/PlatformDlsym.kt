@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import platform.posix.dlsym

internal actual fun platformDlsym(handle: COpaquePointer, symbol: String): CPointer<*>? =
    dlsym(handle, symbol)
