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

listOf("macosArm64", "macosX64").forEach { target ->
    val cap = target.replaceFirstChar { it.uppercase() }
    tasks.register("bundle${cap}App") {
        dependsOn("linkReleaseExecutable${cap}")
        doLast {
            val appDir = layout.buildDirectory.dir("NaHAL.app").get().asFile
            val macosDir = appDir.resolve("Contents/MacOS")
            macosDir.mkdirs()
            val src = layout.buildDirectory.file("bin/$target/releaseExecutable/ui.kexe").get().asFile
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
                    <key>CFBundleIdentifier</key><string>com.helpchoice.nahal.ui</string>
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
        dependsOn("bundle${cap}App")
        commandLine("open", layout.buildDirectory.dir("NaHAL.app").get().asFile.absolutePath)
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
