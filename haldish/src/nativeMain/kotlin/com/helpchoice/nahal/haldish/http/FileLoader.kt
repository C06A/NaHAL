@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.http

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun loadFileBytes(path: String): ByteArray {
    val file = fopen(path, "rb")
        ?: throw IllegalArgumentException("Cannot open file: $path")
    return try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)
        ByteArray(size).also { buf ->
            buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), size.convert(), file)
            }
        }
    } finally {
        fclose(file)
    }
}
