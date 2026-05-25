package com.helpchoice.nahal.haldish.uritemplate

internal object UriTemplateEncoder {

    private val UNRESERVED = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
    private val RESERVED   = setOf(':', '/', '?', '#', '[', ']', '@', '!', '$', '&', '\'',
                                   '(', ')', '*', '+', ',', ';', '=')

    fun encode(value: String, allowReserved: Boolean = false): String = buildString {
        for (ch in value) {
            when {
                ch in UNRESERVED                -> append(ch)
                allowReserved && ch in RESERVED -> append(ch)
                else -> ch.toString().encodeToByteArray().forEach { byte ->
                    append('%')
                    val b = byte.toInt() and 0xFF
                    append(HEX[(b shr 4) and 0xF])
                    append(HEX[b and 0xF])
                }
            }
        }
    }

    private val HEX = "0123456789ABCDEF"
}
