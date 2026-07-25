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

// ── Plugin release artifacts ──────────────────────────────────────────────────
// Each plugin is a runtime-dependency library, one asset per plugin per platform:
//   • haldish-plugin-<name>-<version>.jar          — JVM thin jar (drop into the app's plugins dir,
//                                                     or point HALDISH_PLUGIN_PATH at it)
//   • haldish-plugin-<name>-<platform>-<version>.zip — native libhaldish_plugin.* shared lib +
//                                                     header (point HALDISH_PLUGIN_PATH at the lib;
//                                                     loaded via dlopen)
// Browser JS is intentionally excluded (it has no runtime dynamic-library loading).
data class PluginArtifact(val path: String, val name: String, val linkPrefix: String, val sharedDir: String)

val releasePlugins = listOf(
    PluginArtifact(":plugins:api-key",           "api-key",           "", "releaseShared"),
    PluginArtifact(":plugins:curie",             "curie",             "", "releaseShared"),
    PluginArtifact(":plugins:logger",            "logger",            "", "releaseShared"),
    PluginArtifact(":plugins:bearer-token",      "bearer-token",      "", "releaseShared"),
    PluginArtifact(":plugins:base-url-rewriter", "base-url-rewriter", "", "releaseShared"),
    // chain builds its shared lib from a named "haldish_plugin" binary → different task/dir names.
    PluginArtifact(":plugins:chain",             "chain",             "Haldish_plugin", "haldish_pluginReleaseShared"),
)

fun pluginTaskId(name: String) = name.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }

val pluginJarTasks = releasePlugins.map { p ->
    val gradleName = p.path.substringAfterLast(":")
    tasks.register<Copy>("stagePlugin${pluginTaskId(p.name)}Jar") {
        group       = "release"
        description = "Stages the ${p.name} plugin JVM library jar."
        dependsOn("${p.path}:jvmJar")
        from(project(p.path).layout.buildDirectory.dir("libs")) {
            include("$gradleName-jvm-$releaseVersion.jar")
            rename { "haldish-plugin-${p.name}-$releaseVersion.jar" }
        }
        into(releaseDir)
    }
}

val pluginNativeZipTasks = releasePlugins.flatMap { p ->
    nativePlatforms.map { (target, slug) ->
        val cap = target.replaceFirstChar { it.uppercase() }
        tasks.register<Zip>("stagePlugin${pluginTaskId(p.name)}Native$cap") {
            group       = "release"
            description = "Stages the ${p.name} plugin native shared library for $slug."
            dependsOn("${p.path}:link${p.linkPrefix}ReleaseShared$cap")
            archiveFileName.set("haldish-plugin-${p.name}-$slug-$releaseVersion.zip")
            destinationDirectory.set(releaseDir)
            from(project(p.path).layout.buildDirectory.dir("bin/${target}/${p.sharedDir}")) {
                include("*.so", "*.dylib", "*.dll", "*.h")  // shared lib + generated header
                exclude("**/*.dSYM/**", "*.def")            // drop debug bundles + module defs
            }
        }
    }
}

tasks.register("stagePluginArtifacts") {
    group       = "release"
    description = "Builds and organizes per-plugin runtime-dependency assets (JVM jar + native libs) under build/release (no upload)."
    dependsOn(pluginJarTasks, pluginNativeZipTasks)
    val outDir = releaseDir
    doLast {
        val dir = outDir.get().asFile
        val assets = writeReleaseChecksums(dir)
        logger.lifecycle("Plugin release assets staged in: $dir")
        assets.filter { it.name.startsWith("haldish-plugin-") }.forEach { logger.lifecycle("  • ${it.name}") }
    }
}

// A full release staging includes the plugin assets.
tasks.named("stageReleaseArtifacts") { dependsOn("stagePluginArtifacts") }
