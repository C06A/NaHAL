package com.helpchoice.nahal.haldish.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout

internal actual fun defaultHttpClient(): HttpClient = HttpClient(Js) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
    }
}
