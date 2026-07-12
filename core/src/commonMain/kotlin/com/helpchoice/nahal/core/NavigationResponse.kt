package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument

data class NavigationResponse(
    val raw: HalHttpResponse,
    val document: HalDocument?,
    /** Final request URL actually sent (after `preLink` resolution and template expansion). */
    val url: String = "",
) {
    val isHal: Boolean get() = document != null
    val statusCode: Int get() = raw.statusCode
    val isSuccess: Boolean get() = raw.isSuccess
}
