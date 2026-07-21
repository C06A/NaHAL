// UnsafeNumber: fseek/ftell offsets are C `long` — 32-bit on Windows, 64-bit elsewhere.
// convert() keeps the calls valid on both; >2 GiB files would overflow on mingw only.
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package com.helpchoice.nahal.haldish.http

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun loadFileBytes(path: String): ByteArray {
    val file = fopen(path, "rb")
        ?: throw IllegalArgumentException("Cannot open file: $path")
    return try {
        fseek(file, 0.convert(), SEEK_END)
        val size: Int = ftell(file).convert()
        fseek(file, 0.convert(), SEEK_SET)
        ByteArray(size).also { buf ->
            buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), size.convert(), file)
            }
        }
    } finally {
        fclose(file)
    }
}
