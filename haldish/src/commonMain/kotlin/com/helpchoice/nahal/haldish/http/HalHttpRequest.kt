package com.helpchoice.nahal.haldish.http

import io.ktor.http.HttpMethod

data class HalHttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.Get,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: HalRequestBody = HalRequestBody.None,
    val acceptHal: Boolean = true,
)

sealed class HalRequestBody {
    object None : HalRequestBody()
    data class Text(val content: String, val contentType: String = "text/plain") : HalRequestBody()
    data class UrlEncoded(val params: Map<String, String>) : HalRequestBody()
    data class Json(val content: String) : HalRequestBody()
    data class Binary(val bytes: ByteArray, val contentType: String = "application/octet-stream") : HalRequestBody() {
        override fun equals(other: Any?) = other is Binary && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
    data class FilePath(val path: String, val contentType: String = "application/octet-stream") : HalRequestBody()
    data class Multipart(val parts: List<MultipartPart>) : HalRequestBody()
}

data class MultipartPart(
    val name: String,
    val bytes: ByteArray,
    val fileName: String? = null,
    val contentType: String = "application/octet-stream",
) {
    override fun equals(other: Any?) = other is MultipartPart && name == other.name && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int { var r = name.hashCode(); r = 31 * r + bytes.contentHashCode(); return r }
}
