package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.helpchoice.nahal.ui.component.LocalFilePicker
import com.helpchoice.nahal.ui.component.PickedFile
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "NaHAL") {
        CompositionLocalProvider(
            LocalFilePicker provides { callback ->
                openFilePickerOverlay(callback)
            }
        ) {
            NaHalNavigator()
        }
    }
}

// Shows a native HTML overlay with a visible <input type="file"> so the user
// clicks it directly. This avoids browser user-activation restrictions that
// block programmatic fileInput.click() when called from canvas event handlers.
//
// HTMLElement is intentionally not imported — it lives in kotlin-dom-api-compat,
// whose module initializer crashes on this runtime. Event handlers on plain
// Element nodes are set via asDynamic() instead.
private fun openFilePickerOverlay(callback: (PickedFile?) -> Unit) {
    val body = document.body ?: return

    val overlay = document.createElement("div").also {
        it.setAttribute(
            "style",
            "position:fixed;inset:0;background:rgba(0,0,0,.55);display:flex;" +
                "align-items:center;justify-content:center;z-index:9999",
        )
    }

    val box = document.createElement("div").also {
        it.setAttribute(
            "style",
            "background:#1e1e1e;border:1px solid #444;border-radius:8px;" +
                "padding:24px 28px;display:flex;flex-direction:column;gap:14px;" +
                "align-items:flex-start;min-width:300px;font-family:sans-serif",
        )
    }

    val title = document.createElement("p").also {
        it.textContent = "Load file as request body"
        it.setAttribute("style", "margin:0;color:#ccc;font-size:14px")
    }

    val fileInput = (document.createElement("input") as HTMLInputElement).also {
        it.type = "file"
        it.setAttribute("style", "color:#bbb;font-size:13px;cursor:pointer")
    }

    val cancelBtn = document.createElement("button").also {
        it.textContent = "Cancel"
        it.setAttribute(
            "style",
            "background:#333;color:#aaa;border:1px solid #555;border-radius:4px;" +
                "padding:5px 14px;font-size:12px;cursor:pointer;align-self:flex-end",
        )
    }

    fun close() { body.removeChild(overlay) }

    cancelBtn.asDynamic().onclick = { _: dynamic -> close(); callback(null) }
    overlay.asDynamic().onclick = { e: dynamic ->
        if (e.target == overlay) { close(); callback(null) }
    }

    fileInput.onchange = { _ ->
        val file = fileInput.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = { _ -> close(); callback(PickedFile(file.name, reader.result as String)) }
            reader.onerror = { _ -> close(); callback(null) }
            reader.readAsText(file)
        } else {
            close()
            callback(null)
        }
        Unit
    }

    box.appendChild(title)
    box.appendChild(fileInput)
    box.appendChild(cancelBtn)
    overlay.appendChild(box)
    body.appendChild(overlay)
}
