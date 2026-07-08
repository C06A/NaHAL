package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.core.DocLinkResolver
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.haldish.http.HalHttpRequest
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking

/**
 * The central wrapper. Holds a HAL resource (links, embedded resources, and properties) plus the
 * shared [HalContext], and offers intuitive access to properties, embedded resources by path, and
 * sending HTTP requests by following links.
 *
 * Construct one from a URL, a [HalLink], a raw `Map`, or a parsed [HalDocument] via the
 * [companion][Companion] factories.
 */
class HalResource internal constructor(
    val links: Map<String, List<HalLink>>,
    val embedded: Map<String, List<HalResource>>,
    val properties: Map<String, Any?>,
    val context: HalContext,
) {

    // ── Property access (excludes _links/_embedded, which are not properties) ──────────────────

    /** The value of property [name], coerced to null/Boolean/Number/String/List/Map. */
    fun prop(name: String): Any? = properties[name]

    /** Operator form of [prop] — `resource["total"]`. */
    operator fun get(name: String): Any? = properties[name]

    fun has(name: String): Boolean = properties.containsKey(name)
    fun propertyNames(): Set<String> = properties.keys
    fun linkRels(): Set<String> = links.keys
    fun embeddedRels(): Set<String> = embedded.keys

    /** The first link for [rel], or null. */
    fun link(rel: String): HalLink? = links[rel]?.firstOrNull()

    // ── Embedded access by path ────────────────────────────────────────────────────────────────

    /** Embedded resource under [rel] at [index]. */
    fun embedded(rel: String, index: Int = 0): HalResource? = embedded[rel]?.getOrNull(index)

    /** Embedded resource under [rel] whose properties match every [discriminator] entry. */
    fun embedded(rel: String, discriminator: Map<String, Any?>): HalResource? =
        embedded[rel]?.firstOrNull { res ->
            discriminator.all { (k, v) -> valueEquals(res.properties[k], v) }
        }

    /** Follows a path of [PathStep]s (rel + index-or-discriminator) into nested embedded resources. */
    fun at(vararg steps: PathStep): HalResource? {
        var current: HalResource? = this
        for (step in steps) {
            val c = current ?: return null
            current = if (step.discriminator != null) c.embedded(step.rel, step.discriminator)
                      else c.embedded(step.rel, step.index)
        }
        return current
    }

    // ── Sending requests by following a link ─────────────────────────────────────────────────────

    /**
     * Follows the link [rel] with HTTP [method] (a standard verb or any arbitrary name), applying
     * the modifier chain and session, and returns the [Response].
     */
    fun send(method: String, rel: String, options: SendOptions = SendOptions()): Response {
        val candidates = links[rel] ?: emptyList()
        val link = (options.name?.let { n -> candidates.firstOrNull { it.name == n } }
            ?: candidates.getOrNull(options.index))
            ?: throw NoSuchElementException(
                "No link '$rel'" +
                    (options.name?.let { " named '$it'" } ?: " at index ${options.index}") +
                    " in resource ${links.keys}"
            )
        return dispatch(rel, link, method, options)
    }

    /**
     * Sends a request to an explicit [link] (not looked up by rel) — e.g. a documentation link
     * resolved from `curies`. Runs the same modifier/session chain as [send].
     */
    @JvmOverloads
    fun fetch(link: HalLink, method: String = "GET", options: SendOptions = SendOptions()): Response =
        dispatch("", link, method, options)

    /**
     * The absolute URL the link [rel] would be sent to — after link modifiers (CURIE / SafeCURIE
     * expansion), URI-template expansion with [SendOptions.vars], and base-URL resolution — without
     * sending anything. Useful for asserting SafeCURIE hrefs that point at external targets. Null
     * when no such link exists.
     */
    @JvmOverloads
    fun expandedHref(rel: String, options: SendOptions = SendOptions()): String? {
        val candidates = links[rel] ?: return null
        val link = options.name?.let { n -> candidates.firstOrNull { it.name == n } }
            ?: candidates.getOrNull(options.index)
            ?: return null
        return resolve(rel, link, options).second
    }

    /** Applies link modifiers + template expansion + base-URL resolution, returning (effLink, url). */
    private fun resolve(rel: String, link: HalLink, options: SendOptions): Pair<HalLink, String> {
        // Link-level modifiers (e.g. CURIE) run while we still hold the raw href.
        val holdingDocument = HalDocument(links = links)
        val effLink = context.linkModifiers.fold(link) { l, m -> m.modify(l, rel, holdingDocument, context) }
        val href = effLink.href
        val expanded =
            if (effLink.templated || '{' in href)
                UriTemplate(href).expand(*options.vars.entries.map { it.key to it.value }.toTypedArray())
            else href
        return effLink to resolveUrl(expanded)
    }

    private fun dispatch(rel: String, link: HalLink, method: String, options: SendOptions): Response {
        val (effLink, url) = resolve(rel, link, options)
        var request = HalHttpRequest(
            url = url,
            method = HttpMethod(method),
            headers = options.headers,
            cookies = options.cookies,
            body = options.body,
        )
        request = context.requestModifiers.fold(request) { r, m -> m.modify(r, effLink, context) }

        val response = runBlocking {
            context.session.execute(request) { req -> context.client.execute(req) }
        }
        return Response(response, context, url)
    }

    // ── documentation links via HAL `curies` ─────────────────────────────────────────────────

    /**
     * Resolves the documentation link for relation [rel] from this resource's `curies` (searching
     * outward through [ancestors] to the root), reusing `:core`'s [DocLinkResolver]. [rel] may be
     * CURIE-prefixed (`doc:orders`) or a bare local name (`orders`). Returns null when no `curies`
     * prefix resolves it.
     */
    @JvmOverloads
    fun doc(rel: String, ancestors: List<HalResource> = emptyList()): HalLink? =
        DocLinkResolver.resolve(
            rel,
            HalDocument(links = links),
            ancestors.map { HalDocument(links = it.links) },
        )

    /** Resolves the documentation link for [rel] (see [doc]) and fetches it. */
    @JvmOverloads
    fun openDoc(rel: String, ancestors: List<HalResource> = emptyList()): Response? =
        doc(rel, ancestors)?.let { fetch(it) }

    private fun resolveUrl(href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val base = context.baseUrl
        if (base.isEmpty()) return href
        if (href.startsWith("/")) {
            val origin = Regex("^(https?://[^/]+)").find(base)?.value ?: base.trimEnd('/')
            return origin + href
        }
        return base.trimEnd('/') + "/" + href
    }

    override fun toString(): String =
        "HalResource(links=${links.keys}, embedded=${embedded.keys}, properties=${properties.keys})"

    companion object {
        /** A resource whose only link is `self` pointing at [url]; the context base becomes [url]. */
        @JvmStatic
        fun from(url: String, context: HalContext): HalResource =
            HalResource(mapOf("self" to listOf(HalLink(href = url))), emptyMap(), emptyMap(),
                context.withBaseUrl(url))

        /** A resource whose only link is `self` = [link]. */
        @JvmStatic
        fun from(link: HalLink, context: HalContext): HalResource =
            HalResource(mapOf("self" to listOf(link)), emptyMap(), emptyMap(),
                context.withBaseUrl(link.href))

        /** A resource from a raw HAL map (`_links`/`_embedded` recognised; everything else is a property). */
        @JvmStatic
        fun from(map: Map<String, Any?>, context: HalContext): HalResource {
            val links = parseLinks(map["_links"])
            val embedded = parseEmbedded(map["_embedded"], context)
            val props = map.filterKeys { it != "_links" && it != "_embedded" }
            return HalResource(links, embedded, props, context)
        }

        /** A resource from a parsed [HalDocument] (property JSON values are coerced to Kotlin types). */
        @JvmStatic
        fun from(document: HalDocument, context: HalContext): HalResource {
            val embedded = document.embedded.mapValues { (_, list) -> list.map { from(it, context) } }
            val props = document.properties.mapValues { JsonCoerce.value(it.value) }
            val ctx = document.sourceUrl?.let { context.withBaseUrl(it) } ?: context
            return HalResource(document.links, embedded, props, ctx)
        }

        /** Convenience: start from [url] with a fresh context around [client]/[session]. */
        @JvmStatic
        @JvmOverloads
        fun from(url: String, client: HalHttpClient, session: Session = NoSession): HalResource =
            from(url, HalContext(client = client, session = session))

        // ── map parsing helpers ──────────────────────────────────────────────────────────────

        private fun parseLinks(raw: Any?): Map<String, List<HalLink>> {
            val obj = raw as? Map<*, *> ?: return emptyMap()
            return obj.entries.associate { (k, v) ->
                k.toString() to when (v) {
                    is List<*> -> v.mapNotNull { parseLink(it) }
                    is Map<*, *> -> listOfNotNull(parseLink(v))
                    else -> emptyList()
                }
            }
        }

        private fun parseLink(raw: Any?): HalLink? {
            val m = raw as? Map<*, *> ?: return null
            val href = m["href"]?.toString() ?: return null
            return HalLink(
                href = href,
                templated = (m["templated"] as? Boolean) ?: false,
                type = m["type"]?.toString(),
                name = m["name"]?.toString(),
                title = m["title"]?.toString(),
                hreflang = m["hreflang"]?.toString(),
                profile = m["profile"]?.toString(),
                deprecation = m["deprecation"]?.toString(),
            )
        }

        private fun parseEmbedded(raw: Any?, context: HalContext): Map<String, List<HalResource>> {
            val obj = raw as? Map<*, *> ?: return emptyMap()
            return obj.entries.associate { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                k.toString() to when (v) {
                    is List<*> -> v.filterIsInstance<Map<String, Any?>>().map { from(it, context) }
                    is Map<*, *> -> listOf(from(v as Map<String, Any?>, context))
                    else -> emptyList()
                }
            }
        }

        private fun valueEquals(a: Any?, b: Any?): Boolean =
            if (a is Number && b is Number) a.toDouble() == b.toDouble() else a == b
    }
}
