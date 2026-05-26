package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationResponseTest {

    private fun raw(statusCode: Int = 200, contentType: String? = "application/hal+json") =
        HalHttpResponse(statusCode, emptyMap(), emptyMap(), "{}", contentType)

    @Test fun isHalTrueWhenDocumentPresent() {
        val doc = HalDocument(links = mapOf("self" to listOf(HalLink("/foo"))))
        val response = NavigationResponse(raw = raw(), document = doc)
        assertTrue(response.isHal)
    }

    @Test fun isHalFalseWhenDocumentNull() {
        val response = NavigationResponse(raw = raw(), document = null)
        assertFalse(response.isHal)
    }

    @Test fun statusCodeDelegatesToRaw() {
        val response = NavigationResponse(raw = raw(statusCode = 404, contentType = "text/plain"), document = null)
        assertEquals(404, response.statusCode)
    }

    @Test fun isSuccessDelegatesToRaw() {
        assertTrue(NavigationResponse(raw = raw(statusCode = 200), document = null).isSuccess)
        assertFalse(NavigationResponse(raw = raw(statusCode = 500, contentType = "text/plain"), document = null).isSuccess)
    }
}
