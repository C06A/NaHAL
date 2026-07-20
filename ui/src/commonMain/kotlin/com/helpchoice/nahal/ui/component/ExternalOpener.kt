package com.helpchoice.nahal.ui.component

import androidx.compose.runtime.compositionLocalOf

/**
 * A fetched response body handed to the platform opener. [bytes] are the wire bytes — what a
 * file-writing provider must persist; [text] is the decoded view for platforms that cannot
 * cheaply cross a ByteArray boundary (wasm).
 */
data class ExternalBody(
    val bytes: ByteArray,
    val text: String,
    /** Raw Content-Type header value; may carry parameters (";charset=..."). */
    val contentType: String?,
    /** Pre-derived via [extensionFor], no leading dot (e.g. "svg"). */
    val extension: String,
    /** Final request URL, for provider-side fallbacks. */
    val url: String,
)

/** Platform hook to open a response body with the OS default application for its type. */
data class ExternalOpener(
    /** Display name of the default app for a content type, or null when unknowable. */
    val appNameFor: (contentType: String?) -> String?,
    /** Opens the body externally; false = failed, caller falls back to opening [ExternalBody.url]. */
    val open: (ExternalBody) -> Boolean,
)

/** Null on platforms that cannot open a local file — the UI falls back to opening the URL. */
val LocalExternalOpener = compositionLocalOf<ExternalOpener?> { null }

/** Best-effort temp-file extension from a media type. Inverse of [guessContentType]. */
fun extensionFor(contentType: String?): String {
    val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: ""
    return when {
        "svg" in ct                   -> "svg"    // before "xml" — image/svg+xml
        "json" in ct                  -> "json"   // covers hal+json
        "xhtml" in ct || "html" in ct -> "html"   // before "xml" — application/xhtml+xml
        "xml" in ct                   -> "xml"    // covers hal+xml
        "yaml" in ct                  -> "yaml"
        ct == "application/pdf"       -> "pdf"
        ct == "image/png"             -> "png"
        ct == "image/jpeg"            -> "jpg"
        ct == "image/gif"             -> "gif"
        ct == "image/webp"            -> "webp"
        ct == "text/csv"              -> "csv"
        ct.startsWith("text/")        -> "txt"
        else                          -> "dat"
    }
}
