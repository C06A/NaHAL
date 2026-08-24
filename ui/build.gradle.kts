import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.publish)
}

// ── core's sources, taken as a module dependency ──────────────────────────────
// The source sets below compile `:core` in (see the comment in `sourceSets`). They reach it
// through this configuration, which resolves `project(":core")`'s `coreSourcesElements` variant
// — a dependency edge Gradle knows about, rather than a `../core/src` path that silently rots if
// the module moves. Being a standalone configuration, it is invisible to publication: nothing
// here lands in nahal-ui's POM or module metadata.
val coreSourcesDeps by configurations.dependencyScope("coreSourcesDeps")

val coreSources by configurations.resolvable("coreSources") {
    extendsFrom(coreSourcesDeps)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "kotlin-sources-dir"))
    }
}

dependencies {
    coreSourcesDeps(project(":core"))
}

/** A source directory inside `:core`, e.g. `coreSrcDir("commonMain/kotlin")`. */
fun coreSrcDir(path: String): Provider<File> =
    coreSources.elements.map { it.single().asFile.resolve(path) }

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.helpchoice.nahal.ui.MainKt")
        }
    }

    // Android is a library target here, exactly like every other one — the installable app is the
    // separate :androidApp module, which keeps nahal-ui publishable as a library.
    androidTarget {
        publishLibraryVariants("release")
    }

    js(IR) {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    macosX64 {
        binaries.executable {
            entryPoint = "com.helpchoice.nahal.ui.main"
        }
    }
    macosArm64 {
        binaries.executable {
            entryPoint = "com.helpchoice.nahal.ui.main"
        }
    }

    // Linux and Windows desktop are covered by the jvm() target via Compose Desktop.
    // Native linuxX64/linuxArm64/mingwX64 have no Compose Multiplatform artifacts.

    // The Kotlin/Native + Compose Multiplatform 1.8.x UIKit bindings are generated against the
    // iOS 17 SDK and reference iOS 17 symbols (UITextLoupeSession, UIAccessibilityTraitToggleButton,
    // …). The newest Xcode this macOS (12 Monterey) supports is 14, whose iOS 16 SDK lacks them,
    // so linking the iOS test executables fails on undefined symbols. Resolve them dynamically at
    // load time instead — they are only touched at runtime on iOS 17+. Scoped to the test binaries
    // (the main iOS compilations produce klibs, which don't link). Remove once the toolchain
    // provides an iOS 17+ SDK.
    //
    // Each iOS target also emits NahalUI.framework — the binary the Xcode host in iosApp/ links
    // against, and what makes an installable iOS app possible at all. Static, so the host app
    // links it directly with nothing to embed or re-sign.
    val iosTarget: org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.() -> Unit = {
        binaries.all { linkerOpts("-undefined", "dynamic_lookup") }
        binaries.framework {
            baseName = "NahalUI"
            isStatic = true
        }
    }
    iosX64(configure = iosTarget)
    iosArm64(configure = iosTarget)
    iosSimulatorArm64(configure = iosTarget)

    applyDefaultHierarchyTemplate()

    sourceSets {
        // :core publishes nothing (see core/build.gradle.kts), so a `project(":core")` dependency
        // *in a source set* would leave an unresolvable `com.helpchoice.nahal:nahal-core`
        // coordinate in nahal-ui's POM — Gradle references project dependencies from a
        // publication, it does not inline them. Compiling core's sources here instead makes the
        // published artifact self-contained, and mirrors what the apps already get from static
        // linking. The sources arrive over the `coreSources` configuration declared above, which
        // publication never looks at. Deps travel with the sources: everything core declared is
        // re-declared below.
        //
        // Excluded per platform: the standalone @JsExport / @CName facades (JsCoreNavigator,
        // WasmCoreClient, NativeCoreApi). They exist to expose core to non-Kotlin callers of the
        // `nahal-core` shared library, which :core still builds for the GitHub release; inside a
        // UI artifact they are dead weight, and @CName would export C symbols from NahalUI.framework.

        // Android runs the JVM platform support verbatim — the intermediate source set core used
        // to share CorePlatformSupport.kt between them has to exist here too.
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        jvmAndroidMain.kotlin.srcDir(coreSrcDir("jvmAndroidMain/kotlin"))
        jvmAndroidMain.dependencies {
            implementation(libs.kaml)
        }

        commonMain {
            kotlin.srcDir(coreSrcDir("commonMain/kotlin"))
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                // api, not implementation: NaHalNavigator()'s signature reaches into the core
                // types and through them to haldish, so a consumer of the published nahal-ui
                // cannot compile against it otherwise.
                api(libs.haldish)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jsMain {
            kotlin.srcDir(coreSrcDir("jsMain/kotlin"))
            kotlin.exclude("**/JsCoreNavigator.kt")
            dependencies {
                implementation(libs.kaml)
            }
        }

        wasmJsMain {
            kotlin.srcDir(coreSrcDir("wasmJsMain/kotlin"))
            kotlin.exclude("**/WasmCoreClient.kt")
        }

        nativeMain {
            kotlin.srcDir(coreSrcDir("nativeMain/kotlin"))
            kotlin.exclude("**/NativeCoreApi.kt")
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Puts the framework where iosApp.xcodeproj expects it (FRAMEWORK_SEARCH_PATHS), so the Xcode
// project needs no per-architecture paths baked in. Pick the slice with -Pios.target=…;
// iosSimulatorArm64 is the default because that is what CI builds and what runs without signing.
//   ./gradlew :ui:copyIosFrameworkForXcode -Pios.target=iosArm64 -Pios.config=Release
val copyIosFrameworkForXcode by tasks.registering(Copy::class) {
    group       = "build"
    description = "Copies NahalUI.framework into ui/build/xcode-frameworks for the Xcode host app."

    val iosTargetName = (findProperty("ios.target") ?: "iosSimulatorArm64").toString()
    val iosConfig     = (findProperty("ios.config") ?: "Debug").toString()
    val linkTask      = "link${iosConfig}Framework${iosTargetName.replaceFirstChar { it.uppercase() }}"

    dependsOn(linkTask)
    from(layout.buildDirectory.dir("bin/$iosTargetName/${iosConfig.lowercase()}Framework")) {
        include("NahalUI.framework/**")
    }
    into(layout.buildDirectory.dir("xcode-frameworks"))
}

android {
    namespace  = "com.helpchoice.nahal.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Desktop installers (Compose Desktop, JVM target). Only the format matching the
// build host is produced by `packageDistributionForCurrentOS`:
//   macOS -> .dmg   Linux -> .deb   Windows -> .msi
compose.desktop {
    application {
        mainClass = "com.helpchoice.nahal.ui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Msi)
            packageName    = "NahalNavigator"
            packageVersion = version.toString()
            description    = "Nahal HAL navigator desktop client"
            vendor         = "HelpChoice"
        }
    }
}

// Running the desktop UI with plugins active lives in each plugin's build script, in the separate
// ../HALDiSh_Plugins repository. This build stays plugin-free — see "Plugins in the app" in
// ui/CLAUDE.md.

listOf("macosArm64", "macosX64").forEach { target ->
    val cap = target.replaceFirstChar { it.uppercase() }
    // Per-target output directory. Both bundle tasks used to write the same build/NaHAL.app, so
    // building for both architectures left one bundle holding whichever .kexe was linked last —
    // which made shipping the two of them impossible.
    val appDir     = layout.buildDirectory.dir("macos-app/$target/NaHAL.app")
    val kexe       = layout.buildDirectory.file("bin/$target/releaseExecutable/ui.kexe")
    val appVersion = version.toString()

    tasks.register("bundle${cap}App") {
        group       = "build"
        description = "Assembles NaHAL.app around the $target release executable."
        dependsOn("linkReleaseExecutable${cap}")
        doLast {
            val app = appDir.get().asFile
            val macosDir = app.resolve("Contents/MacOS")
            macosDir.mkdirs()
            val dst = macosDir.resolve("NaHAL")
            kexe.get().asFile.copyTo(dst, overwrite = true)
            dst.setExecutable(true)
            app.resolve("Contents/Info.plist").writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleName</key><string>NaHAL</string>
                    <key>CFBundleDisplayName</key><string>NaHAL</string>
                    <key>CFBundleIdentifier</key><string>com.helpchoice.nahal.ui</string>
                    <key>CFBundleShortVersionString</key><string>$appVersion</string>
                    <key>CFBundleVersion</key><string>$appVersion</string>
                    <key>CFBundleExecutable</key><string>NaHAL</string>
                    <key>NSPrincipalClass</key><string>NSApplication</string>
                    <key>NSHighResolutionCapable</key><true/>
                </dict>
                </plist>
            """.trimIndent())
        }
    }
    tasks.register<Exec>("run${cap}App") {
        group = "run"
        dependsOn("bundle${cap}App")
        commandLine("open", appDir.get().asFile.absolutePath)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "nahal-ui",
        version    = project.version.toString(),
    )

    pom {
        name        = "NaHAL UI"
        description = "Kotlin Multiplatform Compose UI components for the NaHAL client"
        url         = "https://github.com/C06A/NaHAL"
        licenses {
            license {
                name = "Apache-2.0"
                url  = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id   = "C06A"
                name = "CAB"
            }
        }
        scm {
            url = "https://github.com/C06A/NaHAL"
        }
    }
}
