package com.helpchoice.nahal.haldish.http

internal actual fun loadFileBytes(path: String): ByteArray =
    throw UnsupportedOperationException(
        "HalRequestBody.FilePath is not supported in WasmJs. Read the file with the platform API and use HalRequestBody.Binary instead."
    )
