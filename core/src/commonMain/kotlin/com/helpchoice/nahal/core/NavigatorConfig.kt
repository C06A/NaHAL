package com.helpchoice.nahal.core

data class NavigatorConfig(
    val baseUrl: String? = null,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val defaultCookies: Map<String, String> = emptyMap(),
    val properties: Map<String, String> = emptyMap(),
)
