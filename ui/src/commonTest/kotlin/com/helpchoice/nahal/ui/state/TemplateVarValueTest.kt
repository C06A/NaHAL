package com.helpchoice.nahal.ui.state

import com.helpchoice.nahal.ui.model.TemplateVarValue
import com.helpchoice.nahal.ui.model.VarRow
import com.helpchoice.nahal.ui.model.toTemplateArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The template form expresses RFC 6570's three value kinds through the shape of its grid, so the
 * mapping from grid to expanded URL is what these assert — both halves: what [TemplateVarValue]
 * hands to core, and what the expander makes of it.
 */
class TemplateVarValueTest {

    // ── grid shape → value kind ───────────────────────────────────────────────

    @Test
    fun singleRowIsAScalar() {
        assertEquals("42", TemplateVarValue.scalar("42").toTemplateArg())
    }

    @Test
    fun severalRowsAreAList() {
        val v = TemplateVarValue(listOf(VarRow(value = "a"), VarRow(value = "b")))
        assertEquals(listOf("a", "b"), v.toTemplateArg())
    }

    @Test
    fun keyedRowsAreAnAssociativeArray() {
        val v = TemplateVarValue(
            rows = listOf(VarRow("x", "1"), VarRow("y", "2")),
            keyed = true,
        )
        assertEquals(mapOf("x" to "1", "y" to "2"), v.toTemplateArg())
    }

    @Test
    fun keyedRowIsStillAMapWithOneRow() {
        // Unlike the list case, one keyed row does not collapse to a scalar — the name carries meaning.
        val v = TemplateVarValue(listOf(VarRow("only", "1")), keyed = true)
        assertEquals(mapOf("only" to "1"), v.toTemplateArg())
    }

    @Test
    fun blankNamesDropOutOfAnAssociativeArray() {
        // A freshly added row is blank; it must not expand to a stray `=value` pair.
        val v = TemplateVarValue(
            rows = listOf(VarRow("x", "1"), VarRow("", "orphan")),
            keyed = true,
        )
        assertEquals(mapOf("x" to "1"), v.toTemplateArg())
    }

    @Test
    fun isCompoundTracksWhatATextFieldCouldHold() {
        assertTrue(!TemplateVarValue.scalar("1").isCompound)
        assertTrue(TemplateVarValue(listOf(VarRow(value = "a"), VarRow(value = "b"))).isCompound)
        assertTrue(TemplateVarValue(listOf(VarRow("k", "v")), keyed = true).isCompound)
    }

    // ── value kind → expanded URL ─────────────────────────────────────────────

    @Test
    fun scalarExpands() {
        val url = expandTemplate(
            "https://api.example.com/orders/{id}",
            mapOf("id" to TemplateVarValue.scalar("42")),
        )
        assertEquals("https://api.example.com/orders/42", url)
    }

    @Test
    fun listExpandsExplodedAndJoined() {
        val vars = mapOf(
            "segs" to TemplateVarValue(listOf(VarRow(value = "a"), VarRow(value = "b"))),
        )
        assertEquals("/a/b", expandTemplate("{/segs*}", vars))
        // Without the explode modifier the same list is comma-joined.
        assertEquals("a,b", expandTemplate("{segs}", vars))
    }

    @Test
    fun associativeArrayExpandsAsQueryParameters() {
        val vars = mapOf(
            "filter" to TemplateVarValue(
                rows = listOf(VarRow("state", "open"), VarRow("size", "20")),
                keyed = true,
            ),
        )
        assertEquals("?state=open&size=20", expandTemplate("{?filter*}", vars))
    }

    @Test
    fun aVariableWithNoValueLeavesNothingBehind() {
        // The scalar default is an empty row; an unfilled variable must not leave `{id}` in the URL.
        val url = expandTemplate(
            "https://api.example.com/orders{/id}",
            mapOf("id" to TemplateVarValue()),
        )
        assertEquals("https://api.example.com/orders/", url)
    }

    @Test
    fun sendPathReceivesTheDispatchableForms() {
        // What RequestSpec.templateVars gets: String / List / Map, never a TemplateVarValue.
        val args = mapOf(
            "id"     to TemplateVarValue.scalar("42"),
            "segs"   to TemplateVarValue(listOf(VarRow(value = "a"), VarRow(value = "b"))),
            "filter" to TemplateVarValue(listOf(VarRow("state", "open")), keyed = true),
        ).toTemplateArgs()

        assertEquals("42", args["id"])
        assertEquals(listOf("a", "b"), args["segs"])
        assertEquals(mapOf("state" to "open"), args["filter"])
    }
}
