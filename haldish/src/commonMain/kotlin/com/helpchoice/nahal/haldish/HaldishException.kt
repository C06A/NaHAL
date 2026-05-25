package com.helpchoice.nahal.haldish

import com.helpchoice.nahal.haldish.http.HalHttpResponse

sealed class HaldishException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class HalParseException(message: String, cause: Throwable? = null) :
    HaldishException(message, cause)

class UriTemplateException(message: String) :
    HaldishException(message)

class HalHttpException(
    val response: HalHttpResponse,
    message: String = "HTTP ${response.statusCode}",
) : HaldishException(message) {
    val statusCode: Int    get() = response.statusCode
    val body: String       get() = response.body
}
