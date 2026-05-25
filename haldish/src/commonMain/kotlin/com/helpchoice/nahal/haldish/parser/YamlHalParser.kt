package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.model.HalDocument

internal expect object YamlHalParser {
    fun parse(body: String): HalDocument
}
