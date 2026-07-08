package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.parser.HalParser
import java.io.File

/**
 * The result of a [HalResource.send]: status [code], [headers], [cookies], and a body that can be
 * taken in whichever shape the test needs — [asText], [asBytes], [asFile], or [asHal].
 */
class Response internal constructor(
    val raw: HalHttpResponse,
    private val context: HalContext,
    private val requestUrl: String,
) {
    val code: Int get() = raw.statusCode
    val headers: Map<String, List<String>> get() = raw.headers
    val cookies: Map<String, String> get() = raw.cookies
    val contentType: String? get() = raw.contentType
    val isSuccess: Boolean get() = raw.isSuccess
    val isHal: Boolean get() = raw.isHal

    /** Body as text. */
    fun asText(): String = raw.body

    /** Body as the raw bytes read off the wire. */
    fun asBytes(): ByteArray = raw.bytes

    /** Writes the raw body bytes into [file] and returns it. */
    fun asFile(file: File): File {
        file.parentFile?.mkdirs()
        file.writeBytes(raw.bytes)
        return file
    }

    /** Parses the body as HAL and returns a navigable [HalResource] sharing this context. */
    fun asHal(): HalResource {
        val document = HalParser.parse(raw.body, raw.contentType).copy(sourceUrl = requestUrl)
        return HalResource.from(document, context.withBaseUrl(requestUrl))
    }

    override fun toString(): String = "Response(code=$code, contentType=$contentType)"
}
