plugins {
    // AGP is declared here (never applied) so the root and every subproject share one plugin
    // classloader — without it Kotlin's androidTarget() cannot detect the AGP version.
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.jvm)            apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.vanniktech.publish)    apply false
}

allprojects {
    group   = "com.helpchoice.nahal"
    version = "2.1.0"
}

// ── Maven Central bundle slimming ─────────────────────────────────────────────
// Gradle's maven-publish writes md5/sha1/sha256/sha512 next to *every* file it stages,
// including the detached `.asc` GPG signatures — so each artifact ships four checksums of
// its own plus four checksums *of its signature*. The latter are dead weight: Maven Central
// checksums artifacts, not signatures, and no resolver ever requests a `.asc.sha1`. On a
// build publishing this many KMP targets they are roughly 40% of the uploaded bundle.
//
// Dropped here: only `*.asc.md5|sha1|sha256|sha512`.
// Kept: the `.asc` signatures themselves, the artifacts' own checksums, `.pom`, `.module`
// (Gradle needs it to resolve KMP variants), `-sources.jar` and `-javadoc.jar`.
//
// Timing: gradle-maven-publish-plugin 0.35.0 stages into build/publishing/mavenCentral
// (MavenPublishBaseExtension) and only zips + uploads it from MavenCentralBuildService.close(),
// a build-finished hook — so a task finalizer still runs before the bundle is assembled.
// prepareMavenCentralPublishing wipes the staging directory on every run, which keeps the
// publish tasks from going up-to-date and guarantees this finalizer fires each time.
//
// Applied per publishing project: every module stages into its own build/publishing/mavenCentral
// and the zipper walks all of them.
allprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        // Captured at configuration time, resolved inside doLast — configuration-cache safe.
        val stagingDir = layout.buildDirectory.dir("publishing/mavenCentral")

        val pruneSignatureChecksums = tasks.register("pruneSignatureChecksums") {
            group       = "publishing"
            description = "Deletes checksum files of the .asc signatures from the Maven Central staging directory."
            // The staging directory is rewritten by every publish run, so caching a result is meaningless.
            outputs.upToDateWhen { false }

            doLast {
                val dir = stagingDir.get().asFile
                if (!dir.exists()) {
                    logger.info("No Maven Central staging directory at $dir — nothing to prune.")
                    return@doLast
                }
                val pruned = dir.walkTopDown()
                    .filter { it.isFile && it.name.contains(".asc.") }
                    .onEach { it.delete() }
                    .count()
                logger.lifecycle("Pruned $pruned signature checksum file(s) from $dir")
            }
        }

        tasks.withType<PublishToMavenRepository>().configureEach {
            if (name.endsWith("ToMavenCentralRepository")) {
                finalizedBy(pruneSignatureChecksums)
            }
        }
    }
}

// ── GitHub release artifact staging ───────────────────────────────────────────
// Organizes distributable assets into build/release/ grouped for a GitHub release.
// Does NOT upload anything. Run:  ./gradlew stageReleaseArtifacts
//
// Every asset gets a matching .sha256. Groups produced:
//   • NahalNavigator-<version>.dmg/.deb/.msi     — desktop installer, host OS only (JRE embedded)
//   • nahal-app-macos-<arch>-<version>.zip       — NaHAL.app, Kotlin/Native, no JVM at all
//   • nahal-app-ios-simulator-<version>.zip      — unsigned iOS simulator build (CI only, needs Xcode)
//   • nahal-android-<version>.apk / .aab         — Android app / Play Store bundle
//   • nahal-ui-web-<version>.zip                 — browser UI bundle (JS — the compatible one)
//   • nahal-ui-wasm-<version>.zip                — browser UI bundle (WebAssembly; needs a
//                                                  wasm-GC browser, bigger, faster)
//   • nahal-native-<platform>-<version>.zip      — nahal-core shared lib + C header, per platform
//
// No JVM-free desktop app exists for Linux or Windows: Compose Multiplatform ships no
// Kotlin/Native renderer for either. Their .deb/.msi embed a Java runtime instead, so the end
// user still installs nothing extra.
//
// haldish assets (its native libs and the JS/Node library) are released from its own
// repository, ../HALDiSh_KMP — this build only consumes the published artifact.
//
// Maven Central already serves JVM/JS/native klibs, sources, and javadoc for
// Gradle/Maven users — those are intentionally NOT duplicated here.

val releaseVersion = version.toString()
val releaseDir     = layout.buildDirectory.dir("release")

// Gradle native target name -> release asset slug
val nativePlatforms = mapOf(
    "macosArm64" to "macos-arm64",
    "macosX64"   to "macos-x64",
    "linuxX64"   to "linux-x64",
    "mingwX64"   to "windows-x64",
)

val nativeZipTasks = nativePlatforms.map { (target, slug) ->
    val capitalized = target.replaceFirstChar { it.uppercase() } // linkReleaseShared<Target>
    tasks.register<Zip>("zipNative${capitalized}") {
        group       = "release"
        description = "Stages the nahal-core shared library and C header for $slug."
        dependsOn(":core:linkReleaseShared${capitalized}")
        archiveFileName.set("nahal-native-$slug-$releaseVersion.zip")
        destinationDirectory.set(releaseDir)

        val patterns: CopySpec.() -> Unit = {
            include("*.so", "*.dylib", "*.dll", "*.h")   // libs + headers only
            exclude("**/*.dSYM/**", "*.def")             // drop debug bundles + module defs
        }
        from(project(":core").layout.buildDirectory.dir("bin/$target/releaseShared"), patterns)
    }
}

// The JVM-free desktop app: Compose Multiplatform on Kotlin/Native, bundled as NaHAL.app.
// macOS is the only desktop OS this is possible for — Compose Multiplatform ships no Kotlin/Native
// renderer for Linux or Windows (see the empty ui/src/linuxMain and ui/src/mingwMain). Those two
// get a jpackage bundle with an embedded Java runtime instead, so the end user still installs no
// JVM; see stageDesktopInstaller below.
val macosAppZipTasks = listOf("macosArm64" to "macos-arm64", "macosX64" to "macos-x64").map { (target, slug) ->
    val cap = target.replaceFirstChar { it.uppercase() }
    tasks.register<Zip>("zipMacosApp$cap") {
        group       = "release"
        description = "Stages the JVM-free NaHAL.app for $slug."
        dependsOn(":ui:bundle${cap}App")
        archiveFileName.set("nahal-app-$slug-$releaseVersion.zip")
        destinationDirectory.set(releaseDir)
        // Gradle's Zip records unix modes, so Contents/MacOS/NaHAL keeps its executable bit —
        // without it the bundle is unlaunchable. Asserted by the smoke test in the release docs.
        from(project(":ui").layout.buildDirectory.dir("macos-app/$target"))
    }
}

val zipWebUi = tasks.register<Zip>("zipWebUi") {
    group       = "release"
    description = "Stages the browser UI bundle (JS)."
    // `jsBrowserDistribution`, not `jsBrowserProductionWebpack`: the webpack task only emits the
    // compiled ui.js into build/kotlin-webpack/, while the distribution task assembles the
    // servable bundle in build/dist/js/productionExecutable — index.html, the skiko wasm/js
    // runtime and composeResources included.
    dependsOn(":ui:jsBrowserDistribution")
    archiveFileName.set("nahal-ui-web-$releaseVersion.zip")
    destinationDirectory.set(releaseDir)

    val bundleDir = project(":ui").layout.buildDirectory.dir("dist/js/productionExecutable")
    from(bundleDir) { exclude(".gitkeep") }

    // A Zip whose source directory is empty is skipped as NO-SOURCE and stages nothing at all —
    // silently, so the release ends up missing the web bundle. Fail loudly instead.
    doFirst {
        val dir = bundleDir.get().asFile
        val entries = dir.listFiles()?.filterNot { it.name == ".gitkeep" }.orEmpty()
        if (entries.none { it.name == "index.html" }) error(
            "No web bundle in $dir — expected index.html from :ui:jsBrowserDistribution"
        )
    }
}

// The same UI compiled to WebAssembly instead of JS. Shipped alongside the JS bundle rather than
// replacing it: wasm needs a browser with the GC proposal (Chrome/Edge 119+, Firefox 120+,
// Safari 18.2+), so the JS bundle remains the compatible fallback. Browser support is the only
// reason to keep the JS one: at 2.1.0 the wasm zip is the *smaller* download — 4.3 MiB against
// 5.6 MiB — and runs the same Compose UI without a JS interpreter in the draw path.
val zipWebUiWasm = tasks.register<Zip>("zipWebUiWasm") {
    group       = "release"
    description = "Stages the browser UI bundle (WebAssembly)."
    dependsOn(":ui:wasmJsBrowserDistribution")
    archiveFileName.set("nahal-ui-wasm-$releaseVersion.zip")
    destinationDirectory.set(releaseDir)

    val bundleDir = project(":ui").layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    from(bundleDir) { exclude(".gitkeep") }

    // Same NO-SOURCE trap as the JS bundle above: an empty directory would stage nothing, silently.
    doFirst {
        val dir = bundleDir.get().asFile
        val entries = dir.listFiles()?.filterNot { it.name == ".gitkeep" }.orEmpty()
        if (entries.none { it.name == "index.html" }) error(
            "No wasm bundle in $dir — expected index.html from :ui:wasmJsBrowserDistribution"
        )
    }
}

// Desktop installer for the current build host (.dmg on macOS, .deb on Linux,
// .msi on Windows). Compose Desktop only builds the host's format, so a full set
// of installers requires running this on each OS (e.g. a CI matrix).
val stageDesktopInstaller = tasks.register<Copy>("stageDesktopInstaller") {
    group       = "release"
    description = "Stages the desktop installer for the current OS (.dmg/.deb/.msi)."
    dependsOn(":ui:packageDistributionForCurrentOS")
    from(project(":ui").layout.buildDirectory.dir("compose/binaries/main")) {
        include("**/*.dmg", "**/*.deb", "**/*.msi")
    }
    eachFile { path = name }   // flatten into the release dir root
    includeEmptyDirs = false
    into(releaseDir)
}

// Android: the APK is the installable download, the AAB is what a Play Store upload needs.
// Both come from :androidApp; :ui only carries the reusable android library variant.
val stageAndroidApp = tasks.register<Copy>("stageAndroidApp") {
    group       = "release"
    description = "Stages the Android APK and AAB."
    dependsOn(":androidApp:assembleRelease", ":androidApp:bundleRelease")

    val outputs = project(":androidApp").layout.buildDirectory
    from(outputs.dir("outputs/apk/release"))    { include("*.apk") }
    from(outputs.dir("outputs/bundle/release")) { include("*.aab") }
    // androidApp-release.apk -> nahal-android-<version>.apk
    rename("""androidApp-release\.(apk|aab)""", "nahal-android-$releaseVersion.$1")
    includeEmptyDirs = false
    into(releaseDir)
}

// Checksums are a separate task because no single machine can run the full
// `stageReleaseArtifacts` set: Compose Desktop only packages the host's installer format, and
// each native shared library is linked on its own host. CI therefore invokes a per-OS subset of
// the staging tasks above and finishes with this task, which checksums whatever landed in
// build/release. See .github/workflows/release.yml.
val checksumReleaseArtifacts = tasks.register("checksumReleaseArtifacts") {
    group       = "release"
    description = "Writes a .sha256 beside every asset staged in build/release."
    // Which assets are present depends on the host and on which staging tasks ran.
    outputs.upToDateWhen { false }

    val outDir = releaseDir
    doLast {
        val dir = outDir.get().asFile
        val assets = writeReleaseChecksums(dir)
        if (assets.isEmpty()) error("No release assets in $dir — run the staging tasks first.")
        logger.lifecycle("Release assets staged in: $dir")
        assets.forEach { logger.lifecycle("  • ${it.name}") }
    }
}

tasks.register("stageReleaseArtifacts") {
    group       = "release"
    description = "Builds and organizes all GitHub release assets under build/release (no upload)."
    dependsOn(
        nativeZipTasks, macosAppZipTasks, zipWebUi, zipWebUiWasm,
        stageDesktopInstaller, stageAndroidApp,
    )
    finalizedBy(checksumReleaseArtifacts)
}

/** Writes a `<asset>.sha256` next to every release asset in [dir]; returns the assets (sorted). */
fun writeReleaseChecksums(dir: File): List<File> {
    val assetExtensions = setOf("zip", "dmg", "deb", "msi", "jar", "apk", "aab")
    val assets = dir.listFiles { f -> f.isFile && f.extension in assetExtensions }?.sortedBy { it.name } ?: emptyList()
    assets.forEach { asset ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        asset.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        File(dir, "${asset.name}.sha256").writeText("$hex  ${asset.name}\n")
    }
    return assets
}
