package com.helpchoice.nahal.ui.component

import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.ui.model.FetchedResponse
import com.helpchoice.nahal.ui.model.HistoryNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun node(
    id: String,
    parentId: String? = null,
    fromRel: String? = null,
    originStep: PathStep? = null,
) = HistoryNode(
    id = id,
    url = "https://api.test/$id",
    method = "GET",
    requestHeaders = emptyMap(),
    requestCookies = emptyMap(),
    requestBody = null,
    fromRel = fromRel,
    parentId = parentId,
    response = FetchedResponse(
        status = 200,
        statusText = "OK",
        headers = emptyMap(),
        cookies = emptyMap(),
        body = "{}",
        document = null,
    ),
    elapsedMs = 0,
    originStep = originStep,
)

private fun spec(
    id: String,
    parentId: String? = null,
    body: List<String> = listOf("curl -X GET"),
    edgeLabel: String = "",
) = GraphNodeSpec(
    id = id,
    parentId = parentId,
    header = "GET /$id",
    trailer = "200",
    body = body,
    edgeLabel = edgeLabel,
)

class GraphLayoutTest {

    @Test
    fun emptyHistoryHasNoBoxes() {
        val geometry = layoutGraph(emptyList())

        assertTrue(geometry.boxes.isEmpty())
        assertEquals(0, geometry.cols)
        assertEquals(0, geometry.rows)
    }

    @Test
    fun linearChainStepsOneColumnPerHop() {
        val boxes = layoutGraph(listOf(spec("a"), spec("b", "a"), spec("c", "b"))).boxes

        assertEquals(listOf(0, 1, 2), listOf("a", "b", "c").map { boxes.getValue(it).depth })
        assertTrue(boxes.getValue("a").col < boxes.getValue("b").col)
        assertTrue(boxes.getValue("b").col < boxes.getValue("c").col)
        // A chain has one leaf, so every ancestor centres on the same band.
        assertEquals(listOf(0, 0, 0), listOf("a", "b", "c").map { boxes.getValue(it).row })
    }

    @Test
    fun siblingsStackAndTheParentCentresBetweenThem() {
        val boxes = layoutGraph(listOf(spec("a"), spec("b", "a"), spec("c", "a"))).boxes

        assertEquals(boxes.getValue("b").depth, boxes.getValue("c").depth)
        assertEquals(boxes.getValue("b").col, boxes.getValue("c").col)
        assertTrue(boxes.getValue("b").row < boxes.getValue("c").row)
        assertEquals(
            (boxes.getValue("b").row + boxes.getValue("c").row) / 2,
            boxes.getValue("a").row,
        )
    }

    @Test
    fun nodeWhoseParentIsGoneIsARoot() {
        // Defensive: a node pointing at an id that is not in the graph still has to be drawn.
        val boxes = layoutGraph(listOf(spec("a"), spec("b", "missing"))).boxes

        assertEquals(0, boxes.getValue("b").depth)
        assertEquals(0, boxes.getValue("b").col)
        // Two roots, so they take separate bands.
        assertTrue(boxes.getValue("b").row > boxes.getValue("a").row)
    }

    @Test
    fun boxIsSizedToItsWidestLineAndTheTallestBodyInTheGraph() {
        val geometry = layoutGraph(
            listOf(
                spec("a", body = listOf("curl -X GET")),
                spec("b", "a", body = listOf("curl -X POST", "  -H 'accept: application/hal+json'")),
            ),
        )

        // Widest line + two cells of padding on each side; header carries "GET /a" + "200" + gaps.
        assertEquals("  -H 'accept: application/hal+json'".length + 4, geometry.boxes.getValue("b").cols)
        assertEquals("GET /a".length + "200".length + 3 + 4, geometry.boxes.getValue("a").cols)
        // Uniform height: the tallest body (2 lines) plus header, rule and padding.
        assertEquals(6, geometry.boxes.getValue("a").rows)
        assertEquals(6, geometry.boxes.getValue("b").rows)
    }

    @Test
    fun overlongLinesAreClampedToTheMaxBoxWidth() {
        val boxes = layoutGraph(listOf(spec("a", body = listOf("x".repeat(200))))).boxes

        assertEquals(76, boxes.getValue("a").cols)   // 72 characters + 4 of padding
    }

    @Test
    fun aWideEdgeLabelWidensItsGutter() {
        val narrow = layoutGraph(listOf(spec("a"), spec("b", "a", edgeLabel = "next"))).boxes
        val wide = layoutGraph(
            listOf(spec("a"), spec("b", "a", edgeLabel = "search-orders-by-customer")),
        ).boxes

        assertTrue(wide.getValue("b").col > narrow.getValue("b").col)
    }

    @Test
    fun geometryExtentCoversEveryBox() {
        val geometry = layoutGraph(listOf(spec("a"), spec("b", "a"), spec("c", "a")))

        assertEquals(geometry.boxes.values.maxOf { it.col + it.cols }, geometry.cols)
        assertEquals(geometry.boxes.values.maxOf { it.row + it.rows }, geometry.rows)
    }

    @Test
    fun specsCarryTheCurlCommandAndTheLinkRel() {
        val specs = graphSpecs(listOf(node("a"), node("b", parentId = "a", fromRel = "next")))

        assertEquals(listOf(START_NODE_ID, "a", "b"), specs.map { it.id })
        assertEquals("GET /a", specs[1].header)
        assertEquals("200", specs[1].trailer)
        assertEquals("curl -X GET \\", specs[1].body.first())
        assertTrue(specs[1].body.any { it.contains("https://api.test/a") })
        assertEquals("next", specs[2].edgeLabel)
    }

    @Test
    fun aNodeOpenedInPlaceHasNoCurlCommand() {
        val embedded = node("b", parentId = "a", fromRel = "item", originStep = PathStep.Embedded("item", 0))

        val spec = graphSpecs(listOf(node("a"), embedded))[2]

        assertTrue(spec.body.none { it.startsWith("curl") })
    }

    @Test
    fun anEmptyHistoryHasNoStartNode() {
        assertTrue(graphSpecs(emptyList()).isEmpty())
    }

    @Test
    fun everyEntryPointHangsOffTheOneStartNode() {
        // Two address-bar sends (parentId null) plus a link followed from the first.
        val specs = graphSpecs(
            listOf(node("a"), node("b", parentId = "a", fromRel = "next"), node("c")),
        )

        val start = specs.first()
        assertEquals(START_NODE_ID, start.id)
        assertEquals(null, start.parentId)
        assertTrue(start.body.isEmpty())
        assertEquals(listOf(START_NODE_ID, "a", START_NODE_ID), specs.drop(1).map { it.parentId })

        // One root, so the whole session is a single tree: the requests all sit past column 0.
        val boxes = layoutGraph(specs).boxes
        assertEquals(0, boxes.getValue(START_NODE_ID).depth)
        assertEquals(listOf(1, 2, 1), listOf("a", "b", "c").map { boxes.getValue(it).depth })
    }

    @Test
    fun aNodeWhoseParentWasTruncatedFallsBackToStart() {
        val specs = graphSpecs(listOf(node("a"), node("b", parentId = "missing")))

        assertEquals(START_NODE_ID, specs.last().parentId)
    }
}
