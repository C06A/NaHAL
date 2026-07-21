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

// Kotlin/Wasm requires a js() call to be the whole body of a top-level function and to
// return an interop-supported type — hence the formatting happens in JS rather than Kotlin.
private fun jsLocalTimestamp(): String =
    js(
        """(function() {
            var d = new Date();
            function p(n, w) { return String(n).padStart(w, '0'); }
            return p(d.getFullYear(), 4) + p(d.getMonth() + 1, 2) + p(d.getDate(), 2) + 'T' +
                   p(d.getHours(), 2) + p(d.getMinutes(), 2) + p(d.getSeconds(), 2);
        })()"""
    )

internal actual fun platformCurrentTimestamp(): String = jsLocalTimestamp()
