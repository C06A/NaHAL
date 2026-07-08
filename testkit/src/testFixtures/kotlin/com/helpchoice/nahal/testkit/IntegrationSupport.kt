package com.helpchoice.nahal.testkit

import com.helpchoice.nahal.haldish.http.HalHttpClient
import java.io.File

/**
 * Configuration for the integration tests, read from a system property (preferred) or environment
 * variable. Both the target server [url] and the [fixturesDir] must be supplied — there are no
 * bundled fixtures — so the suites skip when either is absent.
 *
 * | Setting      | System property        | Environment variable   |
 * |--------------|------------------------|------------------------|
 * | server URL   | `haldish.it.url`       | `HALDISH_IT_URL`       |
 * | fixtures dir | `haldish.it.fixtures`  | `HALDISH_IT_FIXTURES`  |
 */
object ItConfig {
    val url: String? = setting("haldish.it.url", "HALDISH_IT_URL")
    val fixturesDir: String? = setting("haldish.it.fixtures", "HALDISH_IT_FIXTURES")

    val isConfigured: Boolean get() = !url.isNullOrBlank() && !fixturesDir.isNullOrBlank()

    private fun setting(sysProp: String, envVar: String): String? =
        System.getProperty(sysProp)?.takeIf { it.isNotBlank() }
            ?: System.getenv(envVar)?.takeIf { it.isNotBlank() }
}

/**
 * Shared driver for the integration suites (Kotlin and Groovy). Owns the before-all seeding
 * sequence so both languages run the identical setup against the configured server.
 */
object IntegrationSupport {

    /** A context around a real (JVM/CIO) HTTP client and the given [session]. */
    @JvmOverloads
    fun context(session: Session = NoSession): HalContext = HalContext(HalHttpClient(), session)

    /**
     * Runs the before-all seeding sequence against [url] using the fixture files in [fixturesDir],
     * and returns the freshly-fetched root resource for the follow tests to navigate:
     *
     * 1. GET the root resource
     * 2. DELETE it
     * 3. POST it the contents of `hal_root.yaml`
     * 4. PATCH it a multipart body from `hal_templated.yml` + `hal-json.json`
     * 5. PATCH it the contents of `hal_embedded.json`
     */
    @JvmOverloads
    fun seedRoot(
        context: HalContext,
        url: String = requireNotNull(ItConfig.url) { "haldish.it.url is not configured" },
        fixturesDir: String = requireNotNull(ItConfig.fixturesDir) { "haldish.it.fixtures is not configured" },
    ): HalResource {
        val dir = File(fixturesDir)
        val start = HalResource.from(url, context)

        // 1. get the root resource from the configured URL
        start.send("GET", "self")

        // 2. delete it
        start.send("DELETE", "self")

        // 3. recreate it from hal_root.yaml (a file body)
        start.send(
            "POST", "self",
            SendOptions(body = Body.file(fixture(dir, "hal_root.yaml").path, "application/hal+yaml")),
        )

        // 4. patch it with a multipart body carrying two files
        start.send(
            "PATCH", "self",
            SendOptions(body = Body.multipart {
                file("templated", fixture(dir, "hal_templated.yml").path, "application/hal+yaml")
                file("json", fixture(dir, "hal-json.json").path, "application/hal+json")
            }),
        )

        // 5. patch it with hal_embedded.json (a file body)
        start.send(
            "PATCH", "self",
            SendOptions(body = Body.file(fixture(dir, "hal_embedded.json").path, "application/hal+json")),
        )

        // fetch the seeded root for navigation
        return start.send("GET", "self").asHal()
    }

    private fun fixture(dir: File, name: String): File {
        val file = File(dir, name)
        require(file.isFile) { "Missing fixture file: ${file.path}" }
        return file
    }
}
