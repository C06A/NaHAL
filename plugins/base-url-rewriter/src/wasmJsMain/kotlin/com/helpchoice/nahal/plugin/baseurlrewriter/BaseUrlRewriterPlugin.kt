package com.helpchoice.nahal.plugin.baseurlrewriter

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

/**
 * WasmJS — HALDiSh plugin that resolves relative link hrefs against the base URL of the
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
 * **Per-platform authoring note:** this file is intentionally self-contained (no commonMain).
 * Copy it as a starting point for a WasmJS plugin that needs additional routing logic, e.g.
 * overriding the derived base via a JS-interop call to `window.location.origin`.
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

internal fun computeBase(sourceUrl: String, selfHref: String?): String? {
    if (selfHref.isNullOrEmpty()) return null
    val cleanSource = sourceUrl.substringBefore('#').substringBefore('?').trimEnd('/')
    val selfPath = extractPath(selfHref)?.trimEnd('/') ?: return null
    if (selfPath.isEmpty() || !cleanSource.endsWith(selfPath)) return null
    val base = cleanSource.dropLast(selfPath.length).trimEnd('/')
    return if (base.contains("://")) base else null
}

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

private fun extractOrigin(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val pathStart = url.indexOf('/', schemeEnd + 3)
    return if (pathStart < 0) url else url.substring(0, pathStart)
}

private fun rewriteUrl(original: String, base: String): String {
    if (!original.startsWith('/') && !original.startsWith('?')) return original
    return base + original
}
