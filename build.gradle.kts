plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.vanniktech.publish)    apply false
}

allprojects {
    group   = "com.helpchoice.nahal"
    version = "1.0.1"
}

// ── GitHub release artifact staging ───────────────────────────────────────────
// Organizes distributable assets into build/release/ grouped for a GitHub release.
// Does NOT upload anything. Run:  ./gradlew stageReleaseArtifacts
//
// Groups produced (each a single .zip + a matching .sha256):
//   • nahal-native-<platform>-<version>.zip  — shared lib + C header, per platform
//         (haldish + nahal-core together; consumer grabs only their platform)
//   • haldish-js-<version>.zip               — JS/Node production library
//   • nahal-ui-web-<version>.zip             — browser UI bundle (js/ + wasm/)
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
        description = "Stages haldish + nahal-core shared libraries and C headers for $slug."
        dependsOn(
            ":haldish:linkReleaseShared${capitalized}",
            ":core:linkReleaseShared${capitalized}",
        )
        archiveFileName.set("nahal-native-$slug-$releaseVersion.zip")
        destinationDirectory.set(releaseDir)

        val patterns: CopySpec.() -> Unit = {
            include("*.so", "*.dylib", "*.dll", "*.h")   // libs + headers only
            exclude("**/*.dSYM/**", "*.def")             // drop debug bundles + module defs
        }
        from(project(":haldish").layout.buildDirectory.dir("bin/$target/releaseShared"), patterns)
        from(project(":core").layout.buildDirectory.dir("bin/$target/releaseShared"), patterns)
    }
}

val zipJsLibrary = tasks.register<Zip>("zipJsLibrary") {
    group       = "release"
    description = "Stages the haldish JS/Node production library."
    dependsOn(":haldish:jsProductionLibraryCompileSync")
    archiveFileName.set("haldish-js-$releaseVersion.zip")
    destinationDirectory.set(releaseDir)
    from(project(":haldish").layout.buildDirectory.dir("compileSync/js/main/productionLibrary"))
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
    dependsOn(nativeZipTasks, zipJsLibrary, zipWebUi, stageDesktopInstaller)

    val outDir = releaseDir
    doLast {
        val dir = outDir.get().asFile
        val assetExtensions = setOf("zip", "dmg", "deb", "msi")
        val zips = dir.listFiles { f -> f.isFile && f.extension in assetExtensions }?.sortedBy { it.name } ?: emptyList()
        zips.forEach { zip ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            zip.inputStream().use { ins ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            File(dir, "${zip.name}.sha256").writeText("$hex  ${zip.name}\n")
        }
        logger.lifecycle("Release assets staged in: $dir")
        zips.forEach { logger.lifecycle("  • ${it.name}") }
    }
}
