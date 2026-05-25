package com.helpchoice.nahal.haldish.http

import java.io.File

internal actual fun loadFileBytes(path: String): ByteArray = File(path).readBytes()
