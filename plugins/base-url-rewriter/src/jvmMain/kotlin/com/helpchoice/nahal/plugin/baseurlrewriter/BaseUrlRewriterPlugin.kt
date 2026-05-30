package com.helpchoice.nahal.plugin.baseurlrewriter

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

/**
 * JVM — HALDiSh plugin that resolves relative link hrefs against the base URL of the
 * resource they were taken from.
 *
 * The base is computed from [HalDocument.sourceUrl] and the resource's `self` link:
 * - If the `self` link's path is a suffix of [HalDocument.sourceUrl], the base is
 *   [HalDocument.sourceUrl] with that suffix stripped — preserving any path prefix
 *   (e.g. `/v2`, `/api`) added by a reverse proxy or gateway.
 * - Otherwise the base falls back to the most recently derived base (updated on each
 *   successful match), or to the `configuredBase` constructor argument, and finally
 *   to `scheme://host[:port]` only.
 *
 * If the document has no [HalDocument.sourceUrl] (e.g. an embedded sub-document),
 * the link is returned unchanged.
 *
 * Only hrefs that begin with `/` (path) or `?` (query-only) are modified; absolute hrefs
 * (containing `://`) are passed through unchanged.
 *
 * ### Examples
 * ```
 * // resource loaded from https://gw.example.com/v2/orders/123
 * // self link: /orders/123
 * // base derived: https://gw.example.com/v2
 * "/customers/456"  →  "https://gw.example.com/v2/customers/456"
 * "?page=2"         →  "https://gw.example.com/v2?page=2"
 *
 * // no self link → fallback to origin only
 * // base derived: https://gw.example.com
 * "/customers/456"  →  "https://gw.example.com/customers/456"
 * ```
 *
 * **Per-platform authoring note:** this file is intentionally self-contained (no commonMain).
 * Copy it as a starting point for a JVM plugin that needs additional routing logic, e.g.
 * overriding the derived base with a value from Spring `Environment` or JNDI.
 */
class BaseUrlRewriterPlugin(configuredBase: String? = null) : HaldishPlugin {
    private var cachedBase: String? = configuredBase

    override fun preLink(
        link: HalLink,
        rel: String,
        linkIndex: Int,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep>,
    ): HalLink {
        val sourceUrl = inDocument.sourceUrl ?: return link
        val selfHref = inDocument.link("self")?.href
        val derived = computeBase(sourceUrl, selfHref)
        if (derived != null) cachedBase = derived
        val base = derived ?: cachedBase ?: extractOrigin(sourceUrl) ?: return link
        return link.copy(href = rewriteUrl(link.href, base))
    }
}

/**
 * Derives the base URL from [selfHref] suffix-matching against [sourceUrl].
 * Returns null when there is no self link or the self path is not a suffix of
 * [sourceUrl]'s path — callers should fall back to their cached base or origin.
 */
internal fun computeBase(sourceUrl: String, selfHref: String?): String? {
    if (selfHref.isNullOrEmpty()) return null
    val cleanSource = sourceUrl.substringBefore('#').substringBefore('?').trimEnd('/')
    val selfPath = extractPath(selfHref)?.trimEnd('/') ?: return null
    if (selfPath.isEmpty() || !cleanSource.endsWith(selfPath)) return null
    val base = cleanSource.dropLast(selfPath.length).trimEnd('/')
    return if (base.contains("://")) base else null
}

/**
 * Extracts the path portion from [href], stripping query/fragment and (if absolute)
 * scheme+host. Returns the path starting with `/`, or `null` if none can be determined.
 */
private fun extractPath(href: String): String? {
    val clean = href.substringBefore('#').substringBefore('?')
    val schemeEnd = clean.indexOf("://")
    return if (schemeEnd >= 0) {
        val pathStart = clean.indexOf('/', schemeEnd + 3)
        if (pathStart >= 0) clean.substring(pathStart) else null
    } else {
        if (clean.startsWith('/')) clean else null
    }
}

/**
 * Extracts `scheme://host[:port]` from [url], stripping any path, query, or fragment.
 * Returns `null` if [url] has no `://`.
 */
private fun extractOrigin(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val pathStart = url.indexOf('/', schemeEnd + 3)
    return if (pathStart < 0) url else url.substring(0, pathStart)
}

/**
 * Prepends [base] to [original] when [original] starts with `/` or `?`.
 * Absolute URLs (containing `://`) are returned unchanged.
 */
private fun rewriteUrl(original: String, base: String): String {
    if (!original.startsWith('/') && !original.startsWith('?')) return original
    return base + original
}
