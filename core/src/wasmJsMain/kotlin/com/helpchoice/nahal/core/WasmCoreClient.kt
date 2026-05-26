@file:OptIn(ExperimentalJsExport::class)

package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.parser.HalParser

@JsExport
fun coreLinkHref(halJson: String, rel: String): String? {
    val doc = HalParser.parse(halJson, "application/hal+json")
    return LinkSelector.TopLevel(rel).select(doc)?.href
}

@JsExport
fun coreEmbeddedLinkHref(halJson: String, embeddedRel: String, linkRel: String): String? {
    val doc = HalParser.parse(halJson, "application/hal+json")
    return LinkSelector.InEmbedded(embeddedRel, linkRel = linkRel).select(doc)?.href
}
