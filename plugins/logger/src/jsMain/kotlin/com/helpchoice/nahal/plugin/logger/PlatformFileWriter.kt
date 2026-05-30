package com.helpchoice.nahal.plugin.logger

/**
 * JS implementation: uses Node.js `fs` module when available (Node.js target),
 * falls back to `console.log` in browser environments where there is no filesystem.
 */

private val isNode: Boolean = js("(typeof process !== 'undefined' && process.versions != null && process.versions.node != null)") as Boolean

internal actual fun platformMakeDir(path: String) {
    if (isNode) {
        js("require('fs').mkdirSync(path, { recursive: true })")
    }
    // browser: no-op
}

internal actual fun platformWriteFile(filePath: String, content: String) {
    if (isNode) {
        js("require('fs').writeFileSync(filePath, content, 'utf8')")
    } else {
        console.log("[HALDiSh Logger] $filePath\n$content")
    }
}

internal actual fun platformCurrentTimestamp(): String {
    val d = js("new Date()")
    val year  = (d.getFullYear()  as Int).toString().padStart(4, '0')
    val month = ((d.getMonth() as Int) + 1).toString().padStart(2, '0')
    val day   = (d.getDate()   as Int).toString().padStart(2, '0')
    val hour  = (d.getHours()  as Int).toString().padStart(2, '0')
    val min   = (d.getMinutes() as Int).toString().padStart(2, '0')
    val sec   = (d.getSeconds() as Int).toString().padStart(2, '0')
    return "${year}${month}${day}T${hour}${min}${sec}"
}
