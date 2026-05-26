package com.helpchoice.nahal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigatorConfigTest {

    @Test fun defaultsAreEmpty() {
        val config = NavigatorConfig()
        assertNull(config.baseUrl)
        assertTrue(config.defaultHeaders.isEmpty())
        assertTrue(config.defaultCookies.isEmpty())
        assertTrue(config.properties.isEmpty())
    }

    @Test fun propertiesMapCarriesArbitraryKeys() {
        val config = NavigatorConfig(properties = mapOf("token" to "abc123", "env" to "test"))
        assertEquals("abc123", config.properties["token"])
        assertEquals("test", config.properties["env"])
    }

    @Test fun equalConfigsAreEqual() {
        val a = NavigatorConfig(baseUrl = "https://api.example.com", properties = mapOf("k" to "v"))
        val b = NavigatorConfig(baseUrl = "https://api.example.com", properties = mapOf("k" to "v"))
        assertEquals(a, b)
    }
}
