package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalRequestBody

/**
 * One step in an embedded-resource path. Selects an embedded resource under [rel] by either a
 * numeric [index] or a [discriminator] (a property key=value match) — the intuitive
 * "relation with optional index or key-value discriminator" the requirements call for.
 */
data class PathStep(
    val rel: String,
    val index: Int = 0,
    val discriminator: Map<String, Any?>? = null,
) {
    companion object {
        fun of(rel: String, index: Int = 0): PathStep = PathStep(rel, index)
        fun of(rel: String, discriminator: Map<String, Any?>): PathStep =
            PathStep(rel, discriminator = discriminator)
    }
}

/**
 * Options for [HalResource.send]. Selects the link (by [index] or [name]), expands a templated
 * href with [vars], and adds per-request [headers]/[cookies]/[body]. [body] reuses haldish's
 * [HalRequestBody] so text/json/binary/multipart are all available.
 */
data class SendOptions(
    val index: Int = 0,
    val name: String? = null,
    val vars: Map<String, Any> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: HalRequestBody = HalRequestBody.None,
)
