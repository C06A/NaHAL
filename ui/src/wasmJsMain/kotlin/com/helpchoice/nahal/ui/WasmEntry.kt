package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.helpchoice.nahal.ui.component.ExternalOpener
import com.helpchoice.nahal.ui.component.LocalExternalOpener

// Serves the fetched body as a Blob URL so the browser dispatches by MIME type. Takes the
// decoded string — a ByteArray doesn't cross the wasm/JS boundary directly — so binary
// bodies degrade to their UTF-8 text view on wasm.
private fun openBlobText(text: String, mime: String): Boolean =
    js("window.open(URL.createObjectURL(new Blob([text], { type: mime })), '_blank') != null")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "NaHAL") {
        CompositionLocalProvider(
            LocalExternalOpener provides ExternalOpener(
                appNameFor = { null },   // browsers don't expose handler names
                open = { body ->
                    openBlobText(body.text, body.contentType?.substringBefore(';')?.trim() ?: "text/plain")
                },
            ),
        ) {
            NaHalNavigator()
        }
    }
}
