package com.helpchoice.nahal.testkit

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests against a live [MockingHAL](https://github.com/C06A/MockingHAL) server,
 * seeded from the `haldish` demo fixtures (`hal_root.yaml`, `hal_templated.yml`, `hal-json.json`,
 * `hal_embedded.json`). Configure the server URL with `-Dhaldish.it.url`; the fixtures folder
 * defaults to the sibling `MockingHAL` checkout (override with `-Dhaldish.it.fixtures`). Skipped
 * when the URL is unset:
 *
 * ```
 * ./gradlew :testkit:integrationTest -Dhaldish.it.url=http://localhost:8080/
 * ```
 *
 * `@BeforeAll` runs the seeding sequence ([IntegrationSupport.seedRoot]); the tests then navigate
 * the seeded resources. Assertions track the demo fixtures' contract: the root links to
 * `json-samples`, `links`, `templated`, and `embedded`; `/json-samples` exposes each JSON value
 * shape; `/links/curies` carries SafeCURIE hrefs; `/complex-hal` carries embedded collections.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RootIntegrationTest {

    private lateinit var root: HalResource

    @BeforeAll
    fun seed() {
        assumeTrue(ItConfig.isConfigured, "haldish.it.url / haldish.it.fixtures not configured")
        root = IntegrationSupport.seedRoot(IntegrationSupport.context())
    }

    // ── simple JSON resources: empty, null, true, false, number, structure, array ────────────

    @Test
    fun readsSimpleJsonResources() {
        val json = root.send("GET", "json-samples").asHal()

        // empty → 204 No Content
        assertEquals(204, json.send("GET", "doc:empty").code)

        // scalars — the doc:scalars link array selected by name
        assertEquals("true", scalar(json, "boolean-true"))
        assertEquals("false", scalar(json, "boolean-false"))
        assertEquals("42", scalar(json, "integer"))
        assertEquals("3.14", scalar(json, "number"))
        assertEquals("\"hello world\"", scalar(json, "string"))

        // JSON structure — and null read from within it
        val structure = json.send("GET", "doc:json").asHal()
        assertNull(structure["null_value"])
        assertEquals(42L, structure["integer"])
        assertTrue(structure["nested"] is Map<*, *>)

        // array of JSONs
        assertTrue(json.send("GET", "doc:array").asText().trimStart().startsWith("["))
    }

    private fun scalar(json: HalResource, name: String): String =
        json.send("GET", "doc:scalars", SendOptions(name = name)).asText().trim()

    // ── link responses, including SafeCURIE href handling ────────────────────────────────────

    @Test
    fun followsLinksIncludingSafeCurie() {
        val links = root.send("GET", "links").asHal()

        // ordinary internal links resolve to real resources
        assertTrue(links.send("GET", "simple").isSuccess)
        assertTrue(links.send("GET", "complete").isSuccess)
        assertTrue(links.send("GET", "deprecated").isSuccess)
        assertTrue(links.send("GET", "doc:array").isSuccess)

        // SafeCURIE hrefs point at external targets and MockingHAL serves them literally, so the
        // wrapper's client-side expansion is what we assert (not the response).
        val curies = links.send("GET", "doc:curies").asHal()
        assertEquals("https://stateless.group/hal_specification.html", curies.expandedHref("doc:spec"))
        assertEquals("https://api.example.com/v2/items/42", curies.expandedHref("doc:item"))
        assertEquals("https://api.example.com/v2/items", curies.expandedHref("doc:collection"))
    }

    // ── embedded resources and their links ───────────────────────────────────────────────────

    @Test
    fun accessesEmbeddedResourcesAndTheirLinks() {
        val complex = root.send("GET", "links").asHal().send("GET", "doc:complex").asHal()
        assertEquals(2L, complex["total"])

        val item = complex.embedded("doc:items", 0)
        assertNotNull(item)
        assertEquals("SKU-1", item["sku"])
        assertEquals(true, item["inStock"])
        assertEquals("/complex-hal/items/1", item.link("self")?.href)

        // select an embedded item by a property discriminator
        assertNotNull(complex.embedded("doc:items", mapOf("sku" to "SKU-1")))
        // a single (non-array) embedded resource
        assertNotNull(complex.embedded("doc:author", 0))
    }

    // ── documentation resolved from `curies` for a link's rel ────────────────────────────────

    @Test
    fun resolvesDocumentationFromCuries() {
        // /links/curies declares curies (doc → /docs/curies#{rel}) and doc:-prefixed rels.
        val curies = root.send("GET", "links").asHal().send("GET", "doc:curies").asHal()

        assertEquals("/docs/curies#spec", curies.doc("spec")?.href)
        assertEquals("/docs/curies#item", curies.doc("item")?.href)

        val doc = curies.openDoc("spec")
        assertNotNull(doc)
        assertTrue(doc.isSuccess)                                   // /docs/curies → 200 HTML
        assertTrue(doc.contentType?.contains("html") == true)
    }
}
