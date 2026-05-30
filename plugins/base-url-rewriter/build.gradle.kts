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
