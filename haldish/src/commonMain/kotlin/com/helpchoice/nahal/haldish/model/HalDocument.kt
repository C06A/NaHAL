package com.helpchoice.nahal.haldish.model

import kotlinx.serialization.json.JsonElement

data class HalDocument(
    val links: Map<String, List<HalLink>> = emptyMap(),
    val embedded: Map<String, List<HalDocument>> = emptyMap(),
    val properties: Map<String, JsonElement> = emptyMap(),
    val rawBody: String? = null,
    val items: List<HalDocument> = emptyList(),
    /**
     * The URL this document was fetched from, set by [com.helpchoice.nahal.haldish.http.HalHttpClient]
     * after a successful HTTP response. `null` for embedded sub-documents and documents created
     * directly by parsers (which have no URL context).
     */
    val sourceUrl: String? = null,
) {
    fun linkRels(): Set<String> = links.keys
    fun embeddedRels(): Set<String> = embedded.keys
    fun propertyKeys(): Set<String> = properties.keys

    fun link(rel: String): HalLink? = links[rel]?.firstOrNull()
    fun links(rel: String): List<HalLink> = links[rel] ?: emptyList()
    fun embedded(rel: String): List<HalDocument> = embedded[rel] ?: emptyList()
    fun firstEmbedded(rel: String): HalDocument? = embedded[rel]?.firstOrNull()
}
