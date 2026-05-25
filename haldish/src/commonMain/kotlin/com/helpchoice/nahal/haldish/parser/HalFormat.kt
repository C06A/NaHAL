package com.helpchoice.nahal.haldish.parser

enum class HalFormat { JSON, XML, YAML, UNKNOWN }

object HalFormatDetector {

    fun detect(contentType: String?, body: String): HalFormat {
        if (contentType != null) {
            val ct = contentType.lowercase()
            return when {
                "hal+json"         in ct -> HalFormat.JSON
                "application/json" in ct -> HalFormat.JSON
                "hal+xml"          in ct -> HalFormat.XML
                "application/xml"  in ct -> HalFormat.XML
                "text/xml"         in ct -> HalFormat.XML
                "hal+yaml"         in ct -> HalFormat.YAML
                "application/yaml" in ct -> HalFormat.YAML
                "text/yaml"        in ct -> HalFormat.YAML
                else -> detectFromContent(body)
            }
        }
        return detectFromContent(body)
    }

    fun detectFromContent(body: String): HalFormat {
        val sig = body.trimStart().take(5)
        return when {
            sig.startsWith("{") || sig.startsWith("[") -> HalFormat.JSON
            sig.startsWith("<?xml") || sig.startsWith("<") -> HalFormat.XML
            sig.isNotEmpty() -> HalFormat.YAML
            else -> HalFormat.UNKNOWN
        }
    }
}
