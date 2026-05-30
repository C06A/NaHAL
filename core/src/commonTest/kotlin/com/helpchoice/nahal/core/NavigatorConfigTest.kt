package com.helpchoice.nahal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigatorConfigTest {

    @Test fun defaultsAreEmpty() {
        val config = NavigatorConfig()
        assertTrue(config.defaultHeaders.isEmpty())
        assertTrue(config.defaultCookies.isEmpty())
    }

    @Test fun equalConfigsAreEqual() {
        val a = NavigatorConfig(defaultHeaders = mapOf("X-Key" to "v"))
        val b = NavigatorConfig(defaultHeaders = mapOf("X-Key" to "v"))
        assertEquals(a, b)
    }
}
