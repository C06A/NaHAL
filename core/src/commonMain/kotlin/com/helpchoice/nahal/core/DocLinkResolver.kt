package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate

/**
 * Resolves the documentation URL for a HAL link relation, mirroring HALDiSh's
 * `haldoclink.sh`.
 *
 * The relation's CURIE prefix is matched against a `curies` definition, whose `href` is a
 * URI template (RFC 6570) expanded with `rel=<local-name>`. The lookup starts at the
 * document directly holding the relation and walks **outward through the enclosing
 * resources to the root**, using the first ancestor whose `curies` defines the prefix.
 *
 * Unlike the uppercase `CURIE` collection read by `CuriePlugin` (plain-URL prefix
 * substitution on a link href), this reads the lowercase, HAL-spec `curies` relation and
 * expands its `{rel}` template — producing a *new* documentation link, not a rewritten one.
 *
 * The relation may be given CURIE-prefixed (`doc:orders`) or as a bare local name
 * (`orders`); for a bare name the holding document's link relations are scanned for a key
 * of the form `<prefix>:orders` (first match wins when several prefixes share the suffix).
 */
object DocLinkResolver {

    /** Relation key holding CURIE templates (lowercase, per the HAL spec). */
    const val CURIES_REL: String = "curies"

    /** `type` set on the emitted documentation link. */
    const val DOC_LINK_TYPE: String = "text/html"

    /**
     * @param rel        relation name — CURIE-prefixed (`doc:orders`) or bare (`orders`)
     * @param inDocument the resource directly containing the relation
     * @param ancestors  enclosing resources, **nearest-first** up to the root, searched for
     *                   a `curies` definition when [inDocument] lacks one
     * @return a documentation [HalLink] (`href` = expanded doc URL, `type` = `text/html`),
     *         or `null` when the relation has no resolvable CURIE prefix anywhere in the stack
     */
    fun resolve(
        rel: String,
        inDocument: HalDocument,
        ancestors: List<HalDocument> = emptyList(),
    ): HalLink? {
        val fullRel = if (':' in rel) rel else findSuffixedRel(inDocument, rel) ?: return null
        val colon = fullRel.indexOf(':')
        if (colon < 0) return null
        val prefix = fullRel.substring(0, colon)
        val localName = fullRel.substring(colon + 1)

        // Search from the holding document outward to the root (deepest-first).
        val stack = listOf(inDocument) + ancestors
        for (doc in stack) {
            val curie = doc.links(CURIES_REL).firstOrNull { it.name == prefix } ?: continue
            val href = UriTemplate(curie.href).expand("rel" to localName)
            return HalLink(href = href, type = DOC_LINK_TYPE)
        }
        return null
    }

    /** First link relation of the form `<prefix>:<localName>` in [doc], or `null`. */
    private fun findSuffixedRel(doc: HalDocument, localName: String): String? =
        doc.linkRels().firstOrNull { it.endsWith(":$localName") }
}
