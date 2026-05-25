package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.model.HalDocument

object HalParser {
    fun parse(body: String, contentType: String? = null): HalDocument =
        when (HalFormatDetector.detect(contentType, body)) {
            HalFormat.JSON    -> JsonHalParser.parse(body)
            HalFormat.XML     -> XmlHalParser.parse(body)
            HalFormat.YAML    -> YamlHalParser.parse(body)
            HalFormat.UNKNOWN -> JsonHalParser.parse(body)
        }
}
