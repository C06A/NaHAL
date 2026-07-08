package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.http.HalHttpResponse

/**
 * A request modifier that carries session state. [apply] injects the session's auth into a
 * request; [execute] sends it and owns the response policy — notably the `401` handling that a
 * plain request modifier could not express.
 */
interface Session {

    /** Add this session's authentication to [request]. Default: no change. */
    fun apply(request: HalHttpRequest): HalHttpRequest = request

    /**
     * Send [request] (via [exec]) after applying the session. Default: apply once and send. Token
     * and cookie sessions override this to optionally refresh + retry on a `401`.
     */
    suspend fun execute(
        request: HalHttpRequest,
        exec: suspend (HalHttpRequest) -> HalHttpResponse,
    ): HalHttpResponse = exec(apply(request))
}

/** The "no session" case — passes the request through untouched. */
object NoSession : Session

/**
 * Adds an auth token header (default `Authorization: Bearer <token>`). On a `401`, if
 * [refreshOn401] the credential is refreshed and the request retried once; otherwise the `401`
 * response is returned as-is.
 */
class TokenSession(
    private val userId: String,
    private val credentials: CredentialsProvider,
    private val headerName: String = "Authorization",
    private val scheme: String = "Bearer",
    private val refreshOn401: Boolean = true,
) : Session {

    override fun apply(request: HalHttpRequest): HalHttpRequest {
        val token = credentials.forUser(userId).token()
        val value = if (scheme.isBlank()) token else "$scheme $token"
        return request.copy(headers = request.headers + (headerName to value))
    }

    override suspend fun execute(
        request: HalHttpRequest,
        exec: suspend (HalHttpRequest) -> HalHttpResponse,
    ): HalHttpResponse {
        val first = exec(apply(request))
        if (first.statusCode != 401 || !refreshOn401) return first
        credentials.forUser(userId).refresh()
        return exec(apply(request))
    }
}

/**
 * Adds an auth cookie. On a `401`, if [refreshOn401] the credential is refreshed and the request
 * retried once; otherwise the `401` response is returned as-is.
 */
class CookieSession(
    private val userId: String,
    private val credentials: CredentialsProvider,
    private val cookieName: String = "session",
    private val refreshOn401: Boolean = true,
) : Session {

    override fun apply(request: HalHttpRequest): HalHttpRequest =
        request.copy(cookies = request.cookies + (cookieName to credentials.forUser(userId).cookie()))

    override suspend fun execute(
        request: HalHttpRequest,
        exec: suspend (HalHttpRequest) -> HalHttpResponse,
    ): HalHttpResponse {
        val first = exec(apply(request))
        if (first.statusCode != 401 || !refreshOn401) return first
        credentials.forUser(userId).refresh()
        return exec(apply(request))
    }
}
