package com.helpchoice.nahal.haldish.http

data class HalHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val cookies: Map<String, String>,
    val body: String,
    val contentType: String?,
) {
    val isSuccess: Boolean get() = statusCode in 200..299
    val isHal: Boolean get() = contentType?.lowercase()?.let {
        "hal+json" in it || "hal+xml" in it || "hal+yaml" in it
    } ?: false
}
