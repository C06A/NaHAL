package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpClient

/**
 * Shared execution context threaded through every [HalResource] and [Response]: the wrapped
 * [HalHttpClient], the active [Session], the modifier chain, and the base URL used to resolve
 * relative link hrefs. Followed links inherit the same context, so a whole test scenario shares
 * one session and one modifier set.
 */
class HalContext(
    val client: HalHttpClient,
    val session: Session = NoSession,
    val linkModifiers: List<LinkModifier> = listOf(CurieModifier()),
    val requestModifiers: List<RequestModifier> = listOf(ContentTypeModifier()),
    val baseUrl: String = "",
) {
    fun withBaseUrl(url: String): HalContext =
        HalContext(client, session, linkModifiers, requestModifiers, url)

    fun withSession(session: Session): HalContext =
        HalContext(client, session, linkModifiers, requestModifiers, baseUrl)
}
