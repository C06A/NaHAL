package com.helpchoice.nahal.haldish.uritemplate

import kotlin.test.Test
import kotlin.test.assertEquals

class UriTemplateEncoderTest {

    @Test fun unreservedCharsPassThrough() {
        assertEquals("abcABC123-._~", UriTemplateEncoder.encode("abcABC123-._~"))
    }

    @Test fun spacesEncoded() {
        assertEquals("hello%20world", UriTemplateEncoder.encode("hello world"))
    }

    @Test fun reservedBlockedByDefault() {
        assertEquals("%3A%2F", UriTemplateEncoder.encode(":/"))
    }

    @Test fun reservedAllowedWhenFlagSet() {
        assertEquals(":/", UriTemplateEncoder.encode(":/", allowReserved = true))
    }

    @Test fun percentSignEncoded() {
        assertEquals("%25", UriTemplateEncoder.encode("%"))
    }

    @Test fun unicodeMultibyteEncoded() {
        // U+00E9 = é = 0xC3 0xA9 in UTF-8
        assertEquals("%C3%A9", UriTemplateEncoder.encode("é"))
    }

    @Test fun emptyStringReturnsEmpty() {
        assertEquals("", UriTemplateEncoder.encode(""))
    }
}
