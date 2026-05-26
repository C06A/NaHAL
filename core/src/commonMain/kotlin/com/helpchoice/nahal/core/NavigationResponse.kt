package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument

data class NavigationResponse(
    val raw: HalHttpResponse,
    val document: HalDocument?,
) {
    val isHal: Boolean get() = document != null
    val statusCode: Int get() = raw.statusCode
    val isSuccess: Boolean get() = raw.isSuccess
}
