import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
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

        jvmMain.dependencies {
            implementation(libs.kaml)
        }

        jsMain.dependencies {
            implementation(libs.kaml)
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

// ── Coverage report ───────────────────────────────────────────────────────────

tasks.register("coverageReport") {
    group       = "verification"
    description = "Prints a human-readable summary of the Kover XML coverage report"
    dependsOn("koverXmlReport")

    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/report.xml").get().asFile

        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(reportFile)
        doc.documentElement.normalize()
        val root = doc.documentElement

        fun Element.children(tag: String): List<Element> =
            (0 until childNodes.length)
                .map { childNodes.item(it) }
                .filterIsInstance<Element>()
                .filter { tagName == tag || it.tagName == tag }
                .filter { it.parentNode == this }

        data class Counter(val covered: Int, val total: Int) {
            val pct get() = if (total == 0) 100.0 else covered * 100.0 / total
        }

        fun Element.counters(): Map<String, Counter> =
            children("counter").associate { el ->
                el.getAttribute("type") to Counter(
                    covered = el.getAttribute("covered").toInt(),
                    total   = el.getAttribute("covered").toInt() + el.getAttribute("missed").toInt(),
                )
            }

        fun bar(pct: Double, width: Int = 25): String {
            val filled = (pct / 100.0 * width).toInt().coerceIn(0, width)
            return "█".repeat(filled) + "░".repeat(width - filled)
        }

        fun row(label: String, c: Counter, labelWidth: Int = 13) =
            "  %-${labelWidth}s %s  %5.1f%%  (%4d/%4d)".format(
                label, bar(c.pct), c.pct, c.covered, c.total)

        val sep = "═".repeat(58)
        println()
        println("╔$sep╗")
        println("║${"Coverage Report — :core".padStart(40).padEnd(58)}║")
        println("╚$sep╝")
        println()

        println("OVERALL")
        val overall = root.counters()
        for (type in listOf("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")) {
            overall[type]?.let { println(row(type.lowercase().replaceFirstChar(Char::uppercaseChar), it)) }
        }

        println()
        println("BY PACKAGE  (line coverage)")

        val packages = root.children("package").sortedBy { it.getAttribute("name") }
        val labelWidth = packages.maxOfOrNull { pkg ->
            pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/core/")
                .replace('/', '.')
                .ifEmpty { "(root)" }.length
        }?.coerceAtLeast(7) ?: 7

        packages.forEach { pkg ->
            val name = pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/core/")
                .replace('/', '.')
                .ifEmpty { "(root)" }
            pkg.counters()["LINE"]?.let { println(row(name, it, labelWidth)) }
        }

        println()
        println("BY CLASS  (line coverage)")

        val classLabelWidth = packages.flatMap { it.children("class") }.maxOfOrNull { cls ->
            cls.getAttribute("name").substringAfterLast('/').length
        }?.coerceAtLeast(7) ?: 7

        packages.forEach { pkg ->
            val pkgLabel = pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/core/")
                .replace('/', '.')
                .ifEmpty { "(root)" }
            pkg.children("class").sortedBy { it.getAttribute("name") }.forEach { cls ->
                val clsName = cls.getAttribute("name").substringAfterLast('/')
                cls.counters()["LINE"]?.let {
                    println(row("$pkgLabel/$clsName", it, classLabelWidth + pkgLabel.length + 1))
                }
            }
        }
        println()
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
