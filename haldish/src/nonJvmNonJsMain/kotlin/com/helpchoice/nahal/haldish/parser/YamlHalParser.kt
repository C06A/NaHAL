package com.helpchoice.nahal.haldish.parser

import com.helpchoice.nahal.haldish.model.HalDocument

internal actual object YamlHalParser {
    actual fun parse(body: String): HalDocument =
        throw UnsupportedOperationException(
            "YAML parsing is not supported on this platform. Use JSON or XML HAL documents instead."
        )
}
