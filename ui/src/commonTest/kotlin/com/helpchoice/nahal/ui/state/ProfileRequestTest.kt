package com.helpchoice.nahal.ui.state

import com.helpchoice.nahal.haldish.http.HalHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRequestTest {

    private fun state() = NavigatorState(CoroutineScope(EmptyCoroutineContext))

    @Test
    fun profileIsPreparedVerbatim() {
        val s = state()
        // A profile need not be absolute, or even resolvable — it goes to the plugins as typed.
        s.prepareProfileRequest("Profile name", node = null)

        val req = s.pendingRequest!!
        assertEquals("Profile name", req.url)
        assertEquals("GET", req.method)
        assertEquals("profile", req.fromRel)
        assertTrue(!req.templated)
    }

    @Test
    fun profileCarriesNothingFromTheHoldingLink() {
        val s = state()
        s.prepareProfileRequest("https://schema.org/Person", node = null)

        val req = s.pendingRequest!!
        // No media type of the link's own target, and no ResourcePath — it is a bare-URL send.
        assertNull(req.type)
        assertNull(req.path)
        assertNull(req.rootDocument)
        assertEquals(mapOf("Accept" to HalHttpClient.HAL_ACCEPT), req.headers)
    }

    @Test
    fun templatedProfileGoesToTheExpander() {
        val s = state()
        s.prepareProfileRequest("https://example.com/profiles/{name}", node = null)

        assertTrue(s.pendingRequest!!.templated)
    }
}
