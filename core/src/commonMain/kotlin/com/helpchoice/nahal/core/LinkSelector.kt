package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath

sealed class LinkSelector {

    abstract fun select(resource: HalDocument): HalLink?

    /** The equivalent [ResourcePath] addressing the same link (given to plugins via `preLink`). */
    abstract fun toResourcePath(): ResourcePath

    data class TopLevel(
        val rel: String,
        val index: Int = 0,
    ) : LinkSelector() {
        override fun select(resource: HalDocument): HalLink? =
            resource.links(rel).getOrNull(index)

        override fun toResourcePath(): ResourcePath =
            ResourcePath(listOf(PathStep.Link(rel, index)))

        override fun toString(): String = "TopLevel(rel=$rel, index=$index)"
    }

    data class InEmbedded(
        val embeddedRel: String,
        val embeddedIndex: Int = 0,
        val linkRel: String,
        val linkIndex: Int = 0,
    ) : LinkSelector() {
        override fun select(resource: HalDocument): HalLink? =
            resource.embedded(embeddedRel)
                .getOrNull(embeddedIndex)
                ?.links(linkRel)
                ?.getOrNull(linkIndex)

        override fun toResourcePath(): ResourcePath =
            ResourcePath(listOf(PathStep.Embedded(embeddedRel, embeddedIndex), PathStep.Link(linkRel, linkIndex)))

        override fun toString(): String =
            "InEmbedded(embeddedRel=$embeddedRel[$embeddedIndex], linkRel=$linkRel[$linkIndex])"
    }

    /**
     * Selects a link from one of the documents in a top-level array response
     * ([HalDocument.items]).
     *
     * @param itemIndex   position of the item document within [HalDocument.items]
     * @param linkRel     the relation name (key in `_links`) inside that item
     * @param linkIndex   position of the link within the `_links[linkRel]` array
     */
    data class InItems(
        val itemIndex: Int = 0,
        val linkRel: String,
        val linkIndex: Int = 0,
    ) : LinkSelector() {
        override fun select(resource: HalDocument): HalLink? =
            resource.items
                .getOrNull(itemIndex)
                ?.links(linkRel)
                ?.getOrNull(linkIndex)

        override fun toResourcePath(): ResourcePath =
            ResourcePath(listOf(PathStep.Item(itemIndex), PathStep.Link(linkRel, linkIndex)))

        override fun toString(): String =
            "InItems(itemIndex=$itemIndex, linkRel=$linkRel[$linkIndex])"
    }
}
