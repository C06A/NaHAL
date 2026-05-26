package com.helpchoice.nahal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoreExceptionTest {

    @Test fun noSuchLinkExceptionMessageContainsSelector() {
        val selector = LinkSelector.TopLevel("self")
        val ex = NoSuchLinkException(selector)
        assertTrue(ex.message!!.contains(selector.toString()))
    }

    @Test fun noSuchLinkExceptionCarriesSelector() {
        val selector = LinkSelector.InEmbedded("items", 1, "self")
        val ex = NoSuchLinkException(selector)
        assertEquals(selector, ex.selector)
    }

    @Test fun noSuchLinkExceptionIsCoreException() {
        val ex = NoSuchLinkException(LinkSelector.TopLevel("x"))
        assertIs<CoreException>(ex)
    }
}
