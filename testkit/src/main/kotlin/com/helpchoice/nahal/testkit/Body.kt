package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.http.MultipartPart
import java.io.File

/**
 * Ergonomic constructors for request bodies, so a `send(...)` can carry more than just text: raw
 * bytes, a text or **binary** file, or a **multipart** body mixing several files with key-value
 * form fields in one request. Each returns a [HalRequestBody] for [SendOptions.body].
 */
object Body {

    val none: HalRequestBody get() = HalRequestBody.None

    @JvmOverloads
    fun text(content: String, contentType: String = "text/plain"): HalRequestBody =
        HalRequestBody.Text(content, contentType)

    fun json(content: String): HalRequestBody = HalRequestBody.Json(content)

    @JvmOverloads
    fun bytes(bytes: ByteArray, contentType: String = "application/octet-stream"): HalRequestBody =
        HalRequestBody.Binary(bytes, contentType)

    /** A **binary** file body — the file is read and sent verbatim with [contentType]. */
    @JvmOverloads
    fun file(path: String, contentType: String = "application/octet-stream"): HalRequestBody =
        HalRequestBody.FilePath(path, contentType)

    @JvmOverloads
    fun file(file: File, contentType: String = "application/octet-stream"): HalRequestBody =
        file(file.path, contentType)

    /** A file body read as **text** (decoded) and sent with [contentType]. */
    @JvmOverloads
    fun textFile(path: String, contentType: String = "text/plain"): HalRequestBody =
        HalRequestBody.Text(File(path).readText(), contentType)

    @JvmOverloads
    fun textFile(file: File, contentType: String = "text/plain"): HalRequestBody =
        textFile(file.path, contentType)

    /**
     * A multipart/form-data body. Add key-value fields and file parts:
     *
     * ```
     * Body.multipart {
     *     field("purpose", "seed")
     *     file("config", "/path/hal_root.yaml", "application/hal+yaml")
     *     file("extra",  "/path/hal-json.json", "application/hal+json")
     * }
     * ```
     */
    fun multipart(block: MultipartBuilder.() -> Unit): HalRequestBody =
        HalRequestBody.Multipart(MultipartBuilder().apply(block).parts.toList())
}

/** Builder for a multipart body — see [Body.multipart]. */
class MultipartBuilder {
    internal val parts = mutableListOf<MultipartPart>()

    /** A key-value form field (no filename). */
    @JvmOverloads
    fun field(name: String, value: String, contentType: String = "text/plain"): MultipartBuilder = apply {
        parts += MultipartPart(name, value.encodeToByteArray(), fileName = null, contentType = contentType)
    }

    /** A file part read from [path]; [fileName] defaults to the file's own name. */
    @JvmOverloads
    fun file(
        name: String,
        path: String,
        contentType: String = "application/octet-stream",
        fileName: String? = File(path).name,
    ): MultipartBuilder = apply {
        parts += MultipartPart(name, File(path).readBytes(), fileName = fileName, contentType = contentType)
    }

    @JvmOverloads
    fun file(
        name: String,
        file: File,
        contentType: String = "application/octet-stream",
        fileName: String? = file.name,
    ): MultipartBuilder = file(name, file.path, contentType, fileName)

    /** An in-memory file part. */
    @JvmOverloads
    fun bytes(
        name: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        fileName: String? = null,
    ): MultipartBuilder = apply {
        parts += MultipartPart(name, bytes, fileName = fileName, contentType = contentType)
    }
}
