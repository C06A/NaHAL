plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.jvm)            apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.vanniktech.publish)    apply false
}

allprojects {
    group   = "com.helpchoice.nahal"
    version = "2.0.0"
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
// Groups produced (each a single .zip + a matching .sha256):
//   • nahal-native-<platform>-<version>.zip  — shared lib + C header, per platform
//   • nahal-ui-web-<version>.zip             — browser UI bundle (js/ + wasm/)
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

val zipWebUi = tasks.register<Zip>("zipWebUi") {
    group       = "release"
    description = "Stages the browser UI bundle (JS)."
    // Note: the wasmJs target declares only browser(), no binaries.executable(),
    // so there is no wasm webpack bundle to ship. Add binaries.executable() to
    // the ui wasmJs target if a wasm web build is wanted here.
    dependsOn(":ui:jsBrowserProductionWebpack")
    archiveFileName.set("nahal-ui-web-$releaseVersion.zip")
    destinationDirectory.set(releaseDir)
    from(project(":ui").layout.buildDirectory.dir("dist/js/productionExecutable"))
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

tasks.register("stageReleaseArtifacts") {
    group       = "release"
    description = "Builds and organizes all GitHub release assets under build/release (no upload)."
    dependsOn(nativeZipTasks, zipWebUi, stageDesktopInstaller)

    val outDir = releaseDir
    doLast {
        val dir = outDir.get().asFile
        val assets = writeReleaseChecksums(dir)
        logger.lifecycle("Release assets staged in: $dir")
        assets.forEach { logger.lifecycle("  • ${it.name}") }
    }
}

/** Writes a `<asset>.sha256` next to every release asset in [dir]; returns the assets (sorted). */
fun writeReleaseChecksums(dir: File): List<File> {
    val assetExtensions = setOf("zip", "dmg", "deb", "msi", "jar")
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
