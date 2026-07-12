package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.plugin.curie.CuriePlugin

/**
 * Transforms the selected [HalLink] before its href is expanded and turned into a request —
 * e.g. CURIE prefix expansion, which must happen while we still hold the raw href.
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
 * Expands a CURIE href against the resource's `CURIE` collection. Handles both a bare CURIE
 * (`ord:widget`) and a **SafeCURIE** (`[ord:widget]`) — the brackets are stripped before
 * expansion, per the CURIE spec.
 */
class CurieModifier(private val plugin: CuriePlugin = CuriePlugin()) : LinkModifier {
    override fun modify(
        link: HalLink,
        rel: String,
        inDocument: HalDocument,
        context: HalContext,
    ): HalLink {
        val href = link.href
        val unwrapped =
            if (href.length >= 2 && href.startsWith("[") && href.endsWith("]"))
                link.copy(href = href.substring(1, href.length - 1))
            else link
        return plugin.preLink(unwrapped, ResourcePath.link(rel), inDocument)
    }
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
