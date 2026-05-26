import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
    wasmJs {
        browser()
    }

    linuxX64   { binaries { sharedLib { baseName = "nahal-core" } } }
    linuxArm64()

    macosX64   { binaries { sharedLib { baseName = "nahal-core" } } }
    macosArm64 { binaries { sharedLib { baseName = "nahal-core" } } }

    mingwX64   { binaries { sharedLib { baseName = "nahal-core" } } }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":haldish"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// ── Java test source directory ────────────────────────────────────────────────
// The KMP plugin replaces the Java plugin's default src/test/java with src/jvmTest/java.
// Re-add src/test/java to the compileJvmTestJava compilation task.
tasks.withType<JavaCompile>().configureEach {
    if (name == "compileJvmTestJava") {
        source(fileTree("src/test/java"))
        isEnabled = true
    }
}

// ── Platform test tasks ───────────────────────────────────────────────────────

tasks.register<Exec>("runCoreJsTest") {
    group       = "verification"
    description = "Runs the JavaScript platform test for the core module"
    dependsOn("jsProductionLibraryCompileSync")

    val nodeDir = rootProject.layout.projectDirectory
        .dir("../.gradle/nodejs").asFile
        .walkTopDown()
        .firstOrNull { it.name == "node" && it.parentFile.name == "bin" && it.canExecute() }
        ?: file(System.getProperty("user.home") + "/.nvm/versions/node/v24.15.0/bin/node")

    commandLine(nodeDir, "src/test/js/simple-example.cjs")
    workingDir = projectDir
}

tasks.register<Exec>("compileCoreNativeTest") {
    group       = "verification"
    description = "Compiles the C++ platform test against the macosX64 shared library"
    dependsOn("linkReleaseSharedMacosX64")
    val outDir  = layout.buildDirectory.dir("bin/macosX64/releaseShared").get().asFile
    commandLine(
        "g++", "-std=c++17",
        "-o", temporaryDir.resolve("core_native_test").absolutePath,
        "src/test/cpp/simple_example.cpp",
        "-I${outDir.absolutePath}", "-L${outDir.absolutePath}",
        "-lnahal_core", "-Wl,-rpath,${outDir.absolutePath}",
    )
    workingDir = projectDir
}

tasks.register<Exec>("runCoreNativeTest") {
    group       = "verification"
    description = "Runs the compiled C++ platform test"
    dependsOn("compileCoreNativeTest")
    commandLine(
        tasks.named<Exec>("compileCoreNativeTest").get()
            .temporaryDir.resolve("core_native_test").absolutePath
    )
}

// ── Maven publishing ──────────────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId   = project.group.toString(),
        artifactId = "nahal-core",
        version   = project.version.toString(),
    )

    pom {
        name        = "Nahal Core"
        description = "Kotlin Multiplatform networking and domain layer built on Ktor client"
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
