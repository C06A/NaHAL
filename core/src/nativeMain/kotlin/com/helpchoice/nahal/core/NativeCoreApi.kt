@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.parser.HalParser
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi

@CName("core_link_href")
fun coreLinkHref(halJson: String, rel: String): String? {
    val doc = HalParser.parse(halJson, "application/hal+json")
    return LinkSelector.TopLevel(rel).select(doc)?.href
}

@CName("core_embedded_link_href")
fun coreEmbeddedLinkHref(halJson: String, embeddedRel: String, linkRel: String): String? {
    val doc = HalParser.parse(halJson, "application/hal+json")
    return LinkSelector.InEmbedded(embeddedRel, linkRel = linkRel).select(doc)?.href
}
