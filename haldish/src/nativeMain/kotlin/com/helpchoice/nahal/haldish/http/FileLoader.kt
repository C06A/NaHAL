@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.haldish.http

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun loadFileBytes(path: String): ByteArray {
    val file = fopen(path, "rb")
        ?: throw IllegalArgumentException("Cannot open file: $path")
    return try {
        // Avoid fseek/ftell: their `long` params differ in bit width between
        // mingwX64 (32-bit) and the 64-bit native targets, which the shared
        // nativeMain metadata compiler rejects. Read in chunks instead.
        val chunkSize = 64 * 1024
        var result = ByteArray(0)
        val chunk = ByteArray(chunkSize)
        while (true) {
            val read = chunk.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), chunkSize.convert(), file).toInt()
            }
            if (read <= 0) break
            result += chunk.copyOf(read)
        }
        result
    } finally {
        fclose(file)
    }
}
