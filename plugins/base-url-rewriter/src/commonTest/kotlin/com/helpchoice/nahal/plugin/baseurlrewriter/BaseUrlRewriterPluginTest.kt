package com.helpchoice.nahal.plugin.baseurlrewriter

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.ResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals

class BaseUrlRewriterPluginTest {

    private val plugin = BaseUrlRewriterPlugin()

    private fun doc(sourceUrl: String, selfHref: String? = null) = HalDocument(
        links = if (selfHref != null) mapOf("self" to listOf(HalLink(href = selfHref))) else emptyMap(),
        sourceUrl = sourceUrl,
    )

    private fun rewrite(linkHref: String, inDocument: HalDocument): String =
        plugin.preLink(
            link = HalLink(href = linkHref),
            path = ResourcePath.link("next"),
            rootDocument = inDocument,
        ).href

    // ── self link matches tail of sourceUrl → prefix preserved ───────────────

    @Test
    fun preservesPathPrefixWhenSelfMatchesTail() {
        // gateway adds /v2 prefix; self link is the canonical server path
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun preservesMultiSegmentPrefix() {
        val d = doc("https://gw.example.com/api/v3/orders/123", selfHref = "/orders/123")
        assertEquals("https://gw.example.com/api/v3/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun preservesPrefixForQueryOnlyLink() {
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123")
        assertEquals("https://gw.example.com/v2?cursor=abc", rewrite("?cursor=abc", d))
    }

    @Test
    fun selfLinkAbsoluteUrlPathUsedForMatching() {
        // self href is absolute — path portion extracted for suffix check
        val d = doc(
            sourceUrl = "https://gw.example.com/v2/orders/123",
            selfHref = "https://api.example.com/orders/123",
        )
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun selfMatchesEntirePathLeavingBareOrigin() {
        // no gateway prefix — self path covers the full sourceUrl path
        val d = doc("https://api.example.com/orders/123", selfHref = "/orders/123")
        assertEquals("https://api.example.com/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun sourceUrlQueryStrippedBeforeMatching() {
        val d = doc("https://gw.example.com/v2/orders/123?expand=true", selfHref = "/orders/123")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    // ── self link absent or non-matching → fallback to origin only ────────────

    @Test
    fun fallsBackToOriginWhenNoSelfLink() {
        val d = doc("https://gw.example.com/v2/orders/123")  // no self link
        assertEquals("https://gw.example.com/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun fallsBackToOriginWhenSelfDoesNotMatchTail() {
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/customers/999")
        assertEquals("https://gw.example.com/customers/456", rewrite("/customers/456", d))
    }

    // ── absolute link hrefs always pass through unchanged ────────────────────

    @Test
    fun doesNotModifyAbsoluteHref() {
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123")
        assertEquals(
            "http://other.example.com/orders/1",
            rewrite("http://other.example.com/orders/1", d),
        )
    }

    // ── no sourceUrl → link unchanged ────────────────────────────────────────

    @Test
    fun passesThroughWhenNoSourceUrl() {
        val embedded = HalDocument()  // sourceUrl = null
        assertEquals("/orders/1", rewrite("/orders/1", embedded))
    }

    // ── trailing slash normalisation ─────────────────────────────────────────

    @Test
    fun sourceUrlTrailingSlashMatchesSelfWithoutSlash() {
        val d = doc("https://gw.example.com/v2/orders/123/", selfHref = "/orders/123")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun selfTrailingSlashMatchesSourceWithoutSlash() {
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123/")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun bothTrailingSlashesNormalisedBeforeMatch() {
        val d = doc("https://gw.example.com/v2/orders/123/", selfHref = "/orders/123/")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    // ── query params on sourceUrl and selfHref ────────────────────────────────

    @Test
    fun selfQueryStrippedBeforeMatching() {
        val d = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123?expand=true")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    @Test
    fun bothQueryAndTrailingSlashNormalised() {
        val d = doc("https://gw.example.com/v2/orders/123/?x=1", selfHref = "/orders/123/?expand=true")
        assertEquals("https://gw.example.com/v2/customers/456", rewrite("/customers/456", d))
    }

    // ── computeBase unit tests ────────────────────────────────────────────────

    @Test
    fun computeBasePreservesPrefix() {
        assertEquals(
            "https://gw.example.com/v2",
            computeBase("https://gw.example.com/v2/orders/123", "/orders/123"),
        )
    }

    @Test
    fun computeBaseReturnsNullWhenNoSelf() {
        assertEquals(null, computeBase("https://gw.example.com/v2/orders/123", null))
    }

    @Test
    fun computeBaseReturnsNullWhenSelfDoesNotMatch() {
        assertEquals(null, computeBase("https://gw.example.com/v2/orders/123", "/unrelated/path"))
    }

    @Test
    fun computeBaseSourceTrailingSlash() {
        assertEquals(
            "https://gw.example.com/v2",
            computeBase("https://gw.example.com/v2/orders/123/", "/orders/123"),
        )
    }

    @Test
    fun computeBaseSelfTrailingSlash() {
        assertEquals(
            "https://gw.example.com/v2",
            computeBase("https://gw.example.com/v2/orders/123", "/orders/123/"),
        )
    }

    @Test
    fun computeBaseStripsQueryFromSelf() {
        assertEquals(
            "https://gw.example.com/v2",
            computeBase("https://gw.example.com/v2/orders/123", "/orders/123?expand=true"),
        )
    }

    @Test
    fun computeBaseSourceWithQueryAndSelfWithTrailingSlash() {
        assertEquals(
            "https://gw.example.com/v2",
            computeBase("https://gw.example.com/v2/orders/123?x=1", "/orders/123/"),
        )
    }

    // ── configured base used as initial fallback ──────────────────────────────

    @Test
    fun configuredBaseUsedWhenHeuristicFails() {
        val p = BaseUrlRewriterPlugin(configuredBase = "https://gw.example.com/v2")
        val d = doc("https://gw.example.com/v2/orders/123/item", selfHref = "/items/321")
        assertEquals(
            "https://gw.example.com/v2/orders",
            p.preLink(link = HalLink(href = "/orders"), path = ResourcePath.link("collection"), rootDocument = d).href,
        )
    }

    @Test
    fun configuredBaseUsedWhenNoSelfLink() {
        val p = BaseUrlRewriterPlugin(configuredBase = "https://gw.example.com/v2")
        val d = doc("https://gw.example.com/v2/orders/123")
        assertEquals(
            "https://gw.example.com/v2/customers/456",
            p.preLink(link = HalLink(href = "/customers/456"), path = ResourcePath.link("next"), rootDocument = d).href,
        )
    }

    // ── cached base from prior navigation ────────────────────────────────────

    @Test
    fun cachesDerivedBaseForSubsequentRequests() {
        val p = BaseUrlRewriterPlugin()
        val getDoc = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123")
        p.preLink(link = HalLink(href = "/x"), path = ResourcePath.link("x"), rootDocument = getDoc)
        val postDoc = doc("https://gw.example.com/v2/orders/123/item", selfHref = "/items/321")
        assertEquals(
            "https://gw.example.com/v2/orders",
            p.preLink(link = HalLink(href = "/orders"), path = ResourcePath.link("collection"), rootDocument = postDoc).href,
        )
    }

    @Test
    fun updatesCacheWhenBetterBaseIsDerived() {
        val p = BaseUrlRewriterPlugin(configuredBase = "https://gw.example.com/v1")
        val getDoc = doc("https://gw.example.com/v2/orders/123", selfHref = "/orders/123")
        p.preLink(link = HalLink(href = "/x"), path = ResourcePath.link("x"), rootDocument = getDoc)
        val postDoc = doc("https://gw.example.com/v2/items", selfHref = "/items/321")
        assertEquals(
            "https://gw.example.com/v2/customers",
            p.preLink(link = HalLink(href = "/customers"), path = ResourcePath.link("col"), rootDocument = postDoc).href,
        )
    }
}
