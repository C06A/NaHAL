package com.helpchoice.nahal.haldish.model

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HalDocumentTest {

    private fun doc() = HalDocument(
        links = mapOf(
            "self"   to listOf(HalLink(href = "/orders")),
            "next"   to listOf(HalLink(href = "/orders?page=2")),
            "search" to listOf(HalLink(href = "/orders{?q}", templated = true)),
        ),
        embedded = mapOf(
            "ea:order" to listOf(
                HalDocument(properties = mapOf("total" to JsonPrimitive(30.00))),
                HalDocument(properties = mapOf("total" to JsonPrimitive(20.00))),
            )
        ),
        properties = mapOf(
            "currentlyProcessing" to JsonPrimitive(14),
            "shippedToday"        to JsonPrimitive(20),
        ),
    )

    @Test fun linkRels() =
        assertEquals(setOf("self", "next", "search"), doc().linkRels())

    @Test fun embeddedRels() =
        assertEquals(setOf("ea:order"), doc().embeddedRels())

    @Test fun propertyKeys() =
        assertEquals(setOf("currentlyProcessing", "shippedToday"), doc().propertyKeys())

    @Test fun link() =
        assertEquals("/orders", doc().link("self")?.href)

    @Test fun linkMissing() =
        assertNull(doc().link("prev"))

    @Test fun links() =
        assertEquals(1, doc().links("self").size)

    @Test fun linksMissing() =
        assertEquals(emptyList(), doc().links("prev"))

    @Test fun embedded() =
        assertEquals(2, doc().embedded("ea:order").size)

    @Test fun firstEmbedded() =
        assertEquals(JsonPrimitive(30.00), doc().firstEmbedded("ea:order")?.properties?.get("total"))

    @Test fun embeddedMissing() =
        assertEquals(emptyList(), doc().embedded("ea:widget"))

    @Test fun firstEmbeddedMissing() =
        assertNull(doc().firstEmbedded("ea:widget"))
}
