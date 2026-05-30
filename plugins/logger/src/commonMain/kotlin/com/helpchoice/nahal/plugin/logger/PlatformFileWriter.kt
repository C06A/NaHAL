package com.helpchoice.nahal.plugin.logger

/**
 * Creates all missing directories in [path] (analogous to `mkdir -p`).
 * Silently does nothing if the directory already exists.
 *
 * On browser/WasmJS targets this is a no-op (no writable filesystem is available;
 * the logger falls back to console output on those platforms).
 */
internal expect fun platformMakeDir(path: String)

/**
 * Writes [content] to the file at [filePath], creating or overwriting the file.
 *
 * On browser/WasmJS targets this function prints [content] to the console instead
 * (a writable filesystem is not available in a browser sandbox).
 */
internal expect fun platformWriteFile(filePath: String, content: String)

/**
 * Returns a timestamp string in `yyyyMMddTHHmmss` format using the platform's local time,
 * e.g. `"20260523T143012"`.
 */
internal expect fun platformCurrentTimestamp(): String
