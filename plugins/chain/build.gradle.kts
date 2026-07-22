import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    jvm()

    js(IR) {
        browser()
        nodejs()
        binaries.library()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    linuxX64()
    linuxArm64()

    // The macOS targets additionally build a standalone desktop executable that launches the
    // NaHAL UI with the curie → base-url-rewriter → logger plugins active — the native
    // counterpart of this module's `jvmRun`. Native has no reflection, so the app's own entry
    // point registers the plugins in CorePluginRegistry and points HALDISH_CONFIG at a generated
    // config before handing off to the UI (see src/macosAppMain). It lives in a separate "app"
    // compilation so the published haldish-plugin-chain library klib never depends on :ui.
    val configureChainApp: org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.() -> Unit = {
        val appCompilation = compilations.create("app") {
            defaultSourceSet.kotlin.srcDir("src/macosAppMain/kotlin")
            dependencies {
                implementation(project(":ui"))
                implementation(project(":core"))
                implementation(project(":plugins:curie"))
                implementation(project(":plugins:base-url-rewriter"))
                implementation(project(":plugins:logger"))
            }
        }
        binaries.executable("nahal") {
            compilation = appCompilation
            entryPoint = "com.helpchoice.nahal.plugin.chain.main"
            baseName = "NaHAL"
        }
    }
    macosX64 { configureChainApp() }
    macosArm64 { configureChainApp() }

    mingwX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":haldish"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ── Run the NaHAL desktop UI with this plugin active ─────────────────────────
// Puts this plugin's jvm artifact on the UI's runtime classpath and points HALDISH_CONFIG at
// a generated config file, so :core discovers the plugin at startup and activates it.
val nahalRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // Copy the jvm runtime classpath's attributes so KMP variant resolution selects the
    // *jvm* artifacts of :ui (and its transitive deps).
    val jvmRuntime = configurations.getByName("jvmRuntimeClasspath")
    jvmRuntime.attributes.keySet().forEach { key ->
        @Suppress("UNCHECKED_CAST")
        val typed = key as org.gradle.api.attributes.Attribute<Any>
        attributes.attribute(typed, jvmRuntime.attributes.getAttribute(typed)!!)
    }
}

dependencies {
    nahalRuntime(project(":ui"))
    nahalRuntime(project(":plugins:curie"))
    nahalRuntime(project(":plugins:base-url-rewriter"))
    nahalRuntime(project(":plugins:logger"))
}

val nahalConfig = layout.buildDirectory.file("haldish-config.json")

val writeNahalConfig by tasks.registering {
    description = "Writes the HALDISH_CONFIG file used by jvmRun."
    outputs.file(nahalConfig)
    doLast {
        nahalConfig.get().asFile.writeText(
            """
            {
              "com.helpchoice.nahal.plugin.curie.CuriePlugin": {},
              "com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin": {},
              "com.helpchoice.nahal.plugin.logger.LoggerPlugin": {}
            }
            """.trimIndent()
        )
    }
}

// KGP wires up `jvmRun` (mainClass/classpath) in its own afterEvaluate — reconfigure it in
// ours, which runs later, so these values win.
// `jvmRun` is registered by the Kotlin plugin; `mainRun` is the only hook it honours for the
// main class (a plain `mainClass.set` on the task is overwritten later), while the classpath
// is ours to replace.
kotlin.jvm {
    mainRun { mainClass.set("com.helpchoice.nahal.ui.MainKt") }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "jvmRun") {
        group = "run"
        description = "Runs the NaHAL desktop UI with the curie + base-url-rewriter + logger plugins chained."
        val jvmMain = kotlin.jvm().compilations.getByName("main")
        dependsOn(jvmMain.compileTaskProvider, writeNahalConfig)
        classpath = files(
            jvmMain.output.allOutputs,
            configurations.getByName("jvmRuntimeClasspath"),
            nahalRuntime,
        )
        environment("HALDISH_CONFIG", nahalConfig.get().asFile.absolutePath)
    }
}

// ── Bundle & run the macOS app (mirrors :ui's runMacos*App, but with the plugins active) ──────
// KGP already provides runNahal{Debug,Release}ExecutableMacos{Arm64,X64} in the "run" group,
// which launch the plugin-activating executable directly. These wrappers additionally package it
// as a double-clickable NaHAL.app, matching :ui's runMacos*App tasks.
listOf("macosArm64", "macosX64").forEach { target ->
    val cap = target.replaceFirstChar { it.uppercase() }
    val bundle = tasks.register("bundle${cap}App") {
        group = "build"
        description = "Packages the NaHAL macOS app (chain plugins active) into NaHAL.app."
        dependsOn("linkNahalReleaseExecutable$cap")
        doLast {
            val appDir = layout.buildDirectory.dir("NaHAL.app").get().asFile
            val macosDir = appDir.resolve("Contents/MacOS")
            macosDir.mkdirs()
            val src = layout.buildDirectory.dir("bin/$target/nahalReleaseExecutable").get().asFile
                .listFiles()?.firstOrNull { it.extension == "kexe" }
                ?: error("NaHAL executable not found for $target — did linkNahalReleaseExecutable$cap run?")
            val dst = macosDir.resolve("NaHAL")
            src.copyTo(dst, overwrite = true)
            dst.setExecutable(true)
            appDir.resolve("Contents/Info.plist").writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleName</key><string>NaHAL</string>
                    <key>CFBundleDisplayName</key><string>NaHAL</string>
                    <key>CFBundleIdentifier</key><string>com.helpchoice.nahal.plugin.chain</string>
                    <key>CFBundleVersion</key><string>1.0</string>
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
        description = "Runs the NaHAL macOS app with the curie + base-url-rewriter + logger plugins active."
        dependsOn(bundle)
        commandLine("open", layout.buildDirectory.dir("NaHAL.app").get().asFile.absolutePath)
    }
}

// ── Maven publishing ──────────────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "haldish-plugin-chain",
        version    = project.version.toString(),
    )

    pom {
        name        = "HALDiSh Plugin — Chain"
        description = "HALDiSh plugin combinator that chains multiple plugins in sequence"
        url         = "https://github.com/nahal/nahal"
        licenses {
            license {
                name = "Apache-2.0"
                url  = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id   = "nahal"
                name = "Nahal"
            }
        }
        scm { url = "https://github.com/nahal/nahal" }
    }
}
