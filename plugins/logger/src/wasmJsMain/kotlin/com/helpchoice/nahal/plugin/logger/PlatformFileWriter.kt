package com.helpchoice.nahal.plugin.logger

/**
 * WasmJS implementation: browser sandboxes have no writable filesystem, so all file
 * operations fall back to `console.log`. Bring your own browser storage (IndexedDB,
 * Blob download) by overriding [LoggerPlugin] and replacing these functions.
 */

internal actual fun platformMakeDir(path: String) {
    // no-op in browser WASM
}

internal actual fun platformWriteFile(filePath: String, content: String) {
    println("[HALDiSh Logger] $filePath\n$content")
}

internal actual fun platformCurrentTimestamp(): String {
    // JS Date interop via @JsExport helper — simple epoch-based calculation
    val ms = kotlinx.browser.window.asDynamic().Date.now() as Double
    val totalSec = (ms / 1000).toLong()
    val sec  = (totalSec % 60).toInt()
    val min  = ((totalSec / 60) % 60).toInt()
    val hour = ((totalSec / 3600) % 24).toInt()
    // Date portion via JS interop
    val d = js("new Date()")
    val year  = (d.getFullYear()  as Int).toString().padStart(4, '0')
    val month = ((d.getMonth() as Int) + 1).toString().padStart(2, '0')
    val day   = (d.getDate()   as Int).toString().padStart(2, '0')
    return "${year}${month}${day}T${hour.toString().padStart(2,'0')}${min.toString().padStart(2,'0')}${sec.toString().padStart(2,'0')}"
}
