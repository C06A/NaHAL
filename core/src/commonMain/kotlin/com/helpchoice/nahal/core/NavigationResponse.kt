package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument

data class NavigationResponse(
    val raw: HalHttpResponse,
    /**
     * The body parsed as a document when it is structured data (JSON/XML/YAML) — declared HAL or
     * not; plain JSON parses with every field a property. Null when the body isn't parseable.
     * For "did the server declare HAL", check `raw.isHal`.
     */
    val document: HalDocument?,
    /** Final request URL actually sent (after `preLink` resolution and template expansion). */
    val url: String = "",
) {
    /** A document was parsed — not necessarily declared `hal+*`; see [document]. */
    val isHal: Boolean get() = document != null
    val statusCode: Int get() = raw.statusCode
    val isSuccess: Boolean get() = raw.isSuccess
}
