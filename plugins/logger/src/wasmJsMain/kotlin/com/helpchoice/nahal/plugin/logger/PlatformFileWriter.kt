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

internal actual fun platformCurrentTimestamp(): String = jsTimestamp()

// Kotlin/Wasm requires js("...") to be the single expression of a top-level function
// body and to return an interop-supported type (here, String). Build the whole
// "yyyyMMddTHHmmss" stamp in one JS expression so all fields come from one Date.
private fun jsTimestamp(): String = js(
    "(function(){var d=new Date();var p=function(n,l){return String(n).padStart(l,'0');};" +
    "return p(d.getFullYear(),4)+p(d.getMonth()+1,2)+p(d.getDate(),2)+'T'+" +
    "p(d.getHours(),2)+p(d.getMinutes(),2)+p(d.getSeconds(),2);})()"
)
