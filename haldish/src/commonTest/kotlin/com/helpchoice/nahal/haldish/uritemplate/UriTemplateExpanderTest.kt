package com.helpchoice.nahal.haldish.uritemplate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UriTemplateExpanderTest {

    private fun vars(vararg pairs: Pair<String, String>) = UriTemplateVars.of(*pairs)

    // RFC 6570 Level 1 — simple string expansion
    @Test fun level1SimpleString() {
        val v = vars("var" to "value")
        assertEquals("value", UriTemplate.of("{var}").expand(v))
    }

    @Test fun level1SpecialCharsEncoded() {
        val v = vars("hello" to "Hello World!")
        assertEquals("Hello%20World%21", UriTemplate.of("{hello}").expand(v))
    }

    // RFC 6570 Level 2 — reserved expansion (+) and fragment (#)
    @Test fun level2PlusOperator() {
        val v = vars("path" to "/foo/bar", "x" to "1024")
        assertEquals("/foo/bar,1024", UriTemplate.of("{+path,x}").expand(v))
    }

    @Test fun level2HashOperator() {
        val v = vars("x" to "1024", "y" to "768")
        assertEquals("#1024,768", UriTemplate.of("{#x,y}").expand(v))
    }

    // RFC 6570 Level 3 — multiple expansions
    @Test fun level3DotOperator() {
        val v = vars("x" to "1024", "y" to "768")
        assertEquals(".1024.768", UriTemplate.of("{.x,y}").expand(v))
    }

    @Test fun level3SlashOperator() {
        val v = vars("var" to "value", "x" to "1024")
        assertEquals("/value/1024", UriTemplate.of("{/var,x}").expand(v))
    }

    @Test fun level3SemicolonOperator() {
        val v = vars("x" to "1024", "y" to "768")
        assertEquals(";x=1024;y=768", UriTemplate.of("{;x,y}").expand(v))
    }

    @Test fun level3QueryOperator() {
        val v = vars("x" to "1024", "y" to "768")
        assertEquals("?x=1024&y=768", UriTemplate.of("{?x,y}").expand(v))
    }

    @Test fun level3QueryContinuation() {
        val v = vars("x" to "1024")
        assertEquals("&x=1024", UriTemplate.of("{&x}").expand(v))
    }

    // RFC 6570 Level 4 — modifiers
    @Test fun level4PrefixModifier() {
        val v = vars("var" to "value")
        assertEquals("val", UriTemplate.of("{var:3}").expand(v))
    }

    @Test fun level4ExplodeList() {
        val v = UriTemplateVars().add("list", "red").add("list", "green").add("list", "blue")
        assertEquals("red,green,blue", UriTemplate.of("{list}").expand(v))
        assertEquals("/red/green/blue", UriTemplate.of("{/list*}").expand(v))
    }

    @Test fun level4ExplodeMap() {
        val v = UriTemplateVars().put("keys", "semi", ";").put("keys", "dot", ".").put("keys", "comma", ",")
        // explode maps produce key=value pairs
        val result = UriTemplate.of("{+keys*}").expand(v)
        // Order is map-order so just verify all pairs present
        assertTrue("semi=;" in result, "Expected 'semi=;' in $result")
        assertTrue("dot=." in result, "Expected 'dot=.' in $result")
        assertTrue("comma=," in result, "Expected 'comma=,' in $result")
    }

    // Undefined variable → omitted
    @Test fun undefinedVariableOmitted() {
        val v = vars("x" to "1")
        assertEquals("1", UriTemplate.of("{x,undef}").expand(v))
    }

    // Mixed literal and expression
    @Test fun mixedLiteralAndExpression() {
        val v = vars("id" to "42")
        assertEquals("/orders/42/items", UriTemplate.of("/orders/{id}/items").expand(v))
    }

    // Empty template
    @Test fun emptyTemplate() {
        assertEquals("", UriTemplate.of("").expand(UriTemplateVars()))
    }

    // isTemplated
    @Test fun isTemplated() {
        assertTrue(UriTemplate.of("/orders/{id}").isTemplated())
        assertTrue(!UriTemplate.of("/orders/42").isTemplated())
    }

    // Native Kotlin types

    @Test fun numberValue() {
        val v = UriTemplateVars().set("page", 3).set("size", 25L)
        assertEquals("?page=3&size=25", UriTemplate.of("{?page,size}").expand(v))
    }

    @Test fun booleanValue() {
        val v = UriTemplateVars().set("active", true).set("deleted", false)
        assertEquals("?active=true&deleted=false", UriTemplate.of("{?active,deleted}").expand(v))
    }

    @Test fun listValueViaSet() {
        val v = UriTemplateVars().set("tags", listOf("kotlin", "hal", "rest"))
        assertEquals("?tags=kotlin,hal,rest", UriTemplate.of("{?tags}").expand(v))
        assertEquals("/kotlin/hal/rest", UriTemplate.of("{/tags*}").expand(v))
    }

    @Test fun mapValueViaSet() {
        val v = UriTemplateVars().set("meta", mapOf("lang" to "en", "ver" to "2"))
        val result = UriTemplate.of("{+meta*}").expand(v)
        assertTrue("lang=en" in result, "Expected 'lang=en' in $result")
        assertTrue("ver=2" in result, "Expected 'ver=2' in $result")
    }

    @Test fun ofAcceptsNativeTypes() {
        val v = UriTemplateVars.of("id" to 42, "active" to true)
        assertEquals("/orders/42", UriTemplate.of("/orders/{id}").expand(v))
    }

    @Test fun expandVarargAcceptsNativeTypes() {
        assertEquals("/orders/99", UriTemplate.of("/orders/{id}").expand("id" to 99))
    }

    @Test fun addAcceptsNumbers() {
        val v = UriTemplateVars().add("n", 1).add("n", 2).add("n", 3)
        assertEquals("1,2,3", UriTemplate.of("{n}").expand(v))
    }

    @Test fun putAcceptsNumbers() {
        val v = UriTemplateVars().put("dims", "w", 800).put("dims", "h", 600)
        val result = UriTemplate.of("{+dims*}").expand(v)
        assertTrue("w=800" in result, "Expected 'w=800' in $result")
        assertTrue("h=600" in result, "Expected 'h=600' in $result")
    }
}
