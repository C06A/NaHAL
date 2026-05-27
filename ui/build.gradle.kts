import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.helpchoice.nahal.ui.MainKt")
        }
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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":core"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "nahal-ui",
        version    = project.version.toString(),
    )

    pom {
        name        = "Nahal UI"
        description = "Kotlin Multiplatform Compose UI components for the Nahal client"
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
        scm {
            url = "https://github.com/nahal/nahal"
        }
    }
}
