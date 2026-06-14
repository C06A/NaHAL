package com.helpchoice.nahal.plugin.curie

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.plugin.EmbeddingStep
import com.helpchoice.nahal.haldish.plugin.HaldishPlugin

/**
 * HALDiSh plugin that expands a CURIE-prefixed link href into a full URL, mirroring
 * the behaviour of HALDiSh's `halcurie.sh` link plugin.
 *
 * A href "looks like a CURIE" when it is `<prefix>:<reference>` with an NCName
 * `<prefix>` (e.g. `ord:widget`). The prefix is resolved against the first link in a
 * [CURIE_REL] collection whose [HalLink.name] equals the prefix, searched from the
 * document directly holding the link upward through the embedding stack to the root.
 *
 * Unlike the lowercase `curies` of the HAL spec, a [CURIE_REL] href is a plain URL with
 * no `{rel}` template; expansion simply replaces the `<prefix>:` of the original href
 * with that URL — so `ord:widget` against `https://api.example.com/orders/` becomes
 * `https://api.example.com/orders/widget`.
 *
 * Because a URL scheme also parses as `<prefix>:…` (e.g. `http://host/…`), an href whose
 * scheme has no matching [CURIE_REL] definition is left unchanged — as are a prefix-less
 * or absent href, an unknown prefix, and a prefix that is not a valid NCName.
 *
 * Resolution happens in the [preLink] hook, so it applies to any link followed via a
 * relation. Combine with other plugins via
 * [com.helpchoice.nahal.plugin.chain.ChainPlugin].
 */
class CuriePlugin : HaldishPlugin {

    override fun preLink(
        link: HalLink,
        rel: String,
        linkIndex: Int,
        inDocument: HalDocument,
        embeddingPath: List<EmbeddingStep>,
    ): HalLink {
        val href = link.href
        // Not a CURIE-looking href → pass through unchanged.
        val colon = href.indexOf(':')
        if (colon < 0) return link

        val prefix = href.substring(0, colon)
        val reference = href.substring(colon + 1)
        if (!NCNAME.matches(prefix)) return link

        // Search from the link's own document upward through the embedding stack to the
        // root (nearest ancestor first); take the first CURIE definition named `prefix`.
        val ancestorsNearestFirst =
            listOf(inDocument) + embeddingPath.asReversed().map { it.inDocument }

        val curieHref = ancestorsNearestFirst
            .asSequence()
            .mapNotNull { doc -> doc.links(CURIE_REL).firstOrNull { it.name == prefix } }
            .firstOrNull()
            ?.href
            ?: return link

        return link.copy(href = curieHref + reference)
    }

    companion object {
        /** Relation key holding CURIE definitions (uppercase/singular, per HALDiSh). */
        const val CURIE_REL: String = "CURIE"

        /** XML NCName: a prefix must match this to be treated as a CURIE prefix. */
        private val NCNAME = Regex("^[A-Za-z_][A-Za-z0-9._-]*$")
    }
}
