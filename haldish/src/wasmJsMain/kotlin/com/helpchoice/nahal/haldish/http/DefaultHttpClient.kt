package com.helpchoice.nahal.haldish.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun defaultHttpClient(): HttpClient = HttpClient(Js)
