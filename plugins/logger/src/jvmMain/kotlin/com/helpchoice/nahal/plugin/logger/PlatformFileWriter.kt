package com.helpchoice.nahal.plugin.logger

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

internal actual fun platformMakeDir(path: String) {
    File(path).mkdirs()
}

internal actual fun platformWriteFile(filePath: String, content: String) {
    File(filePath).writeText(content, Charsets.UTF_8)
}

internal actual fun platformCurrentTimestamp(): String =
    LocalDateTime.now().format(TIMESTAMP_FMT)
