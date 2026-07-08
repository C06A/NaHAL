package com.helpchoice.nahal.testkit

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The auth material a [Session] needs for one user. All accessors default to empty so an
 * implementation supplies only what its session kind uses (token vs cookie).
 */
interface Credentials {
    /** Bearer/token value (no scheme prefix) for a [TokenSession]. */
    fun token(): String = ""

    /** Cookie value for a [CookieSession]. */
    fun cookie(): String = ""

    /** Re-acquire the credential (called by a session after a `401` when refresh is enabled). */
    fun refresh() {}
}

/** Resolves [Credentials] for a user identifier — the config lookup the requirements describe. */
interface CredentialsProvider {
    fun forUser(userId: String): Credentials
}

/**
 * Mutable in-memory credentials, convenient for tests: [refresh] runs [onRefresh], letting a test
 * simulate a server-side rotation by swapping the token/cookie.
 */
class SimpleCredentials(
    private var tokenValue: String = "",
    private var cookieValue: String = "",
    private val onRefresh: (SimpleCredentials) -> Unit = {},
) : Credentials {
    override fun token(): String = tokenValue
    override fun cookie(): String = cookieValue
    fun setToken(value: String) { tokenValue = value }
    fun setCookie(value: String) { cookieValue = value }
    override fun refresh() { onRefresh(this) }
}

/** Credentials supplied inline, keyed by user id — the default for tests. */
class MapCredentialsProvider(
    private val users: Map<String, Credentials>,
) : CredentialsProvider {
    override fun forUser(userId: String): Credentials =
        users[userId] ?: throw NoSuchElementException("No credentials configured for user '$userId'")
}

/**
 * Reads credentials from a JSON or YAML config file (path from the `HALDISH_CONFIG` env var by
 * default), keyed by user id. The file is re-read on every [forUser]/refresh so an externally
 * rotated token is picked up. Expected shape:
 *
 * ```yaml
 * users:
 *   alice: { token: "ey...", cookie: "sid=..." }
 * ```
 */
class FileCredentialsProvider(
    private val path: String = System.getenv(ENV_VAR)
        ?: error("$ENV_VAR is not set and no path was supplied"),
) : CredentialsProvider {

    override fun forUser(userId: String): Credentials {
        val file = File(path)
        return object : Credentials {
            override fun token(): String = entry(file, userId).token
            override fun cookie(): String = entry(file, userId).cookie
            // Nothing to do — token()/cookie() already re-read the file each call.
            override fun refresh() {}
        }
    }

    private fun entry(file: File, userId: String): CredEntry {
        val text = file.readText()
        val parsed = when (file.extension.lowercase()) {
            "yaml", "yml" -> Yaml.default.decodeFromString(CredsFile.serializer(), text)
            else          -> Json.decodeFromString(CredsFile.serializer(), text)
        }
        return parsed.users[userId]
            ?: throw NoSuchElementException("No credentials for user '$userId' in $path")
    }

    @Serializable
    private data class CredsFile(val users: Map<String, CredEntry> = emptyMap())

    @Serializable
    private data class CredEntry(val token: String = "", val cookie: String = "")

    companion object {
        const val ENV_VAR: String = "HALDISH_CONFIG"
    }
}
