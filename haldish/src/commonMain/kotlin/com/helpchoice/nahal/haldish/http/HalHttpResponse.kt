package com.helpchoice.nahal.haldish.http

data class HalHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val cookies: Map<String, String>,
    val body: String,
    val contentType: String?,
    /**
     * Raw response body bytes, preserving binary content that the text [body] view may lose.
     * Defaults to the UTF-8 encoding of [body] so existing constructions remain valid; the
     * HTTP client populates it with the true bytes read off the wire.
     *
     * Note: because [ByteArray] uses referential equality, two responses with equal byte
     * contents are not `equals` — compare individual fields rather than whole instances.
     */
    val bytes: ByteArray = body.encodeToByteArray(),
) {
    val isSuccess: Boolean get() = statusCode in 200..299
    val isHal: Boolean get() = contentType?.lowercase()?.let {
        "hal+json" in it || "hal+xml" in it || "hal+yaml" in it
    } ?: false
}
