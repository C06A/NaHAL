package com.helpchoice.nahal.haldish.model

data class HalLink(
    val href: String,
    val templated: Boolean = false,
    val type: String? = null,
    val name: String? = null,
    val title: String? = null,
    val hreflang: String? = null,
    val profile: String? = null,
    val deprecation: String? = null,
)
