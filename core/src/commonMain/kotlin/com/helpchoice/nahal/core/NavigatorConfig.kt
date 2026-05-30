package com.helpchoice.nahal.core

data class NavigatorConfig(
    val defaultHeaders: Map<String, String> = emptyMap(),
    val defaultCookies: Map<String, String> = emptyMap(),
)
