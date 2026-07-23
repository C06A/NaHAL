@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.helpchoice.nahal.plugin.logger

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native file I/O shared across all native targets (linux, apple, mingw) using
 * portable C stdio. Directory creation differs per platform (POSIX 2-arg `mkdir`
 * vs Windows 1-arg `_mkdir`) and lives in `platformMakeDir` actuals under the
 * posixMain and mingwMain source sets.
 */

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
