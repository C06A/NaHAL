import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.publish)
}

// ── Per-platform plugin: deliberate design ────────────────────────────────────
//
// BaseUrlRewriterPlugin has NO commonMain implementation. Each platform's source set
// carries its own standalone class. This module exists as a teaching example of
// the per-platform authoring pattern — copy the relevant *Main file as a starting
// point when your plugin needs platform-specific URL handling or routing logic.

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
    macosX64()
    macosArm64()
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
}

val nahalConfig = layout.buildDirectory.file("haldish-config.json")

val writeNahalConfig by tasks.registering {
    description = "Writes the HALDISH_CONFIG file used by jvmRun."
    outputs.file(nahalConfig)
    doLast {
        nahalConfig.get().asFile.writeText(
            """
            {
              "com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin": {}
            }
            """.trimIndent()
        )
    }
}

// `jvmRun` is registered by the Kotlin plugin; `mainRun` is the only hook it honours for the
// main class (a plain `mainClass.set` on the task is overwritten later), while the classpath
// is ours to replace.
kotlin.jvm {
    mainRun { mainClass.set("com.helpchoice.nahal.ui.MainKt") }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "jvmRun") {
        group = "run"
        description = "Runs the NaHAL desktop UI with the base-url-rewriter plugin (resolves relative link hrefs)."
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

// ── Maven publishing ──────────────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "haldish-plugin-base-url-rewriter",
        version    = project.version.toString(),
    )

    pom {
        name        = "HALDiSh Plugin — Base URL Rewriter (per-platform)"
        description = "HALDiSh plugin that rewrites the scheme and host of every outgoing request. " +
                      "Per-platform authoring example — each platform source set is self-contained."
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
