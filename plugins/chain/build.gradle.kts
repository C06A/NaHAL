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
    nahalRuntime(project(":plugins:curie"))
    nahalRuntime(project(":plugins:base-url-rewriter"))
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
              "com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin": {}
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
        description = "Runs the NaHAL desktop UI with the curie + base-url-rewriter plugins chained."
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
