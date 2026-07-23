@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.plugin

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import platform.windows.GetProcAddress

internal actual fun platformDlsym(handle: COpaquePointer, symbol: String): CPointer<*>? =
    GetProcAddress(handle.reinterpret(), symbol)
