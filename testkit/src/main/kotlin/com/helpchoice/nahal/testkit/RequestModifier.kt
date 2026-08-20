package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink

/**
 * Transforms the selected [HalLink] before its href is expanded and turned into a request —
 * any rewrite that must happen while we still hold the raw href.
 */
fun interface LinkModifier {
    fun modify(link: HalLink, rel: String, inDocument: HalDocument, context: HalContext): HalLink
}

/**
 * Transforms the built [HalHttpRequest] before it is handed to the session for sending —
 * e.g. setting `Content-Type` from the link's declared media type.
 */
fun interface RequestModifier {
    fun modify(request: HalHttpRequest, link: HalLink, context: HalContext): HalHttpRequest
}

/**
 * Sets the request `Content-Type` from the link's [HalLink.type] when a body is present and no
 * explicit `Content-Type` was already supplied.
 */
class ContentTypeModifier : RequestModifier {
    override fun modify(request: HalHttpRequest, link: HalLink, context: HalContext): HalHttpRequest {
        val type = link.type ?: return request
        if (request.body is HalRequestBody.None) return request
        if (request.headers.keys.any { it.equals("Content-Type", ignoreCase = true) }) return request
        return request.copy(headers = request.headers + ("Content-Type" to type))
    }
}
