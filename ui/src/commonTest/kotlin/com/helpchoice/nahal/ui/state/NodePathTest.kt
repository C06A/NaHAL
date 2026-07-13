package com.helpchoice.nahal.ui.state

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.ui.model.FetchedResponse
import com.helpchoice.nahal.ui.model.HistoryNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NodePathTest {

    private fun node(
        id: String,
        doc: HalDocument?,
        parentId: String? = null,
        originStep: PathStep? = null,
    ) = HistoryNode(
        id = id, url = "https://x/$id", method = "GET",
        requestHeaders = emptyMap(), requestCookies = emptyMap(), requestBody = null,
        fromRel = null, parentId = parentId,
        response = FetchedResponse(
            status = 200, statusText = "OK",
            headers = emptyMap(), cookies = emptyMap(), body = "", document = doc,
        ),
        elapsedMs = 0, originStep = originStep,
    )

    private val terminal = listOf(PathStep.Link("next", 0))

    @Test fun fetchedNodeHasNoPrefix() {
        val doc = HalDocument(links = mapOf("next" to listOf(HalLink("https://x/next"))))
        val fetched = node("1", doc)

        val rooted = resolveNodePath(listOf(fetched), fetched, terminal)!!
        assertEquals(terminal, rooted.path.steps)
        assertSame(doc, rooted.rootDocument)
    }

    @Test fun embeddedNodesRootAtFetchedAncestor() {
        // root ──_embedded.orders[1]──> order ──_embedded.items[2]──> item
        val rootDoc = HalDocument()
        val orderDoc = HalDocument()
        val itemDoc = HalDocument()
        val root = node("1", rootDoc)
        val order = node("2", orderDoc, parentId = "1", originStep = PathStep.Embedded("orders", 1))
        val item = node("3", itemDoc, parentId = "2", originStep = PathStep.Embedded("items", 2))

        val rooted = resolveNodePath(listOf(root, order, item), item, terminal)!!
        assertEquals(
            listOf(PathStep.Embedded("orders", 1), PathStep.Embedded("items", 2)) + terminal,
            rooted.path.steps,
        )
        // Rooted at the fetched document — that is what lets plugins walk the embedding stack.
        assertSame(rootDoc, rooted.rootDocument)
    }

    @Test fun arrayItemAtHeadIsExpressible() {
        val rootDoc = HalDocument()
        val root = node("1", rootDoc)
        val item = node("2", HalDocument(), parentId = "1", originStep = PathStep.Item(3))

        val rooted = resolveNodePath(listOf(root, item), item, terminal)!!
        assertEquals(listOf(PathStep.Item(3)) + terminal, rooted.path.steps)
    }

    @Test fun arrayItemInsideEmbeddedIsNotExpressible() {
        // Grammar allows Item only at the head, so Embedded → Item has no ResourcePath.
        // Caller falls back to addressing the terminal against the node's own document.
        val root = node("1", HalDocument())
        val embedded = node("2", HalDocument(), parentId = "1", originStep = PathStep.Embedded("orders", 0))
        val item = node("3", HalDocument(), parentId = "2", originStep = PathStep.Item(1))

        assertNull(resolveNodePath(listOf(root, embedded, item), item, terminal))
    }

    @Test fun propertyTerminalRootsAtAncestorToo() {
        val rootDoc = HalDocument()
        val root = node("1", rootDoc)
        val embedded = node("2", HalDocument(), parentId = "1", originStep = PathStep.Embedded("orders", 0))
        val propTerminal = listOf(PathStep.Property("items", 0), PathStep.Property("url"))

        val rooted = resolveNodePath(listOf(root, embedded), embedded, propTerminal)!!
        assertEquals(listOf(PathStep.Embedded("orders", 0)) + propTerminal, rooted.path.steps)
        assertSame(rootDoc, rooted.rootDocument)
    }

    @Test fun brokenAncestorChainYieldsNull() {
        val orphan = node("2", HalDocument(), parentId = "missing", originStep = PathStep.Embedded("x", 0))
        assertNull(resolveNodePath(listOf(orphan), orphan, terminal))
    }

    // ── display label ────────────────────────────────────────────────────────

    @Test fun jsonPathLabelRendersIndices() {
        assertEquals(
            "items[0].url",
            listOf(PathStep.Property("items", 0), PathStep.Property("url")).jsonPathLabel(),
        )
        assertEquals("mirrors[1]", listOf(PathStep.Property("mirrors", 1)).jsonPathLabel())
        assertEquals("data.href", listOf(PathStep.Property("data"), PathStep.Property("href")).jsonPathLabel())
    }
}
