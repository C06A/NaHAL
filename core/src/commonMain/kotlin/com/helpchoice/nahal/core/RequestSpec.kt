package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.ResourcePath
import io.ktor.http.HttpMethod

/**
 * Everything a caller (e.g. the UI) has assembled for one request, handed to [HalNavigator.send].
 * The caller does no URL manipulation itself — [HalNavigator.send] resolves the target, runs the
 * `preLink` hook (where CURIE / base-url rewriting happens), expands the URI template, sends, and
 * parses.
 *
 * Provide either [path] (a link/property inside a resource) or [url] (a bare URL, e.g. the address
 * bar):
 *  - **[path] set** — resolved against [rootDocument] and passed through `preLink`, so a plugin can
 *    rewrite/absolutise it (e.g. against `rootDocument.sourceUrl`), then template-expanded. The
 *    relation name plugins see is [ResourcePath.terminalRel]; a path with no terminal resolves to
 *    the reached resource's `self` link.
 *  - **[url] set** — used as-is (template-expanded). No `preLink`, so it must be absolute unless a
 *    plugin on the raw path handles it.
 */
data class RequestSpec(
    val url: String? = null,
    val path: ResourcePath? = null,
    /** The document [path] is resolved against; required when [path] is set. */
    val rootDocument: HalDocument? = null,
    val method: HttpMethod = HttpMethod.Get,
    val templateVars: Map<String, Any> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: HalRequestBody = HalRequestBody.None,
    /** When true the client adds the HAL `Accept` header; the UI sets its own Accept and passes false. */
    val acceptHal: Boolean = true,
)
