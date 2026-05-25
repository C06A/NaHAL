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
    jvm {
        withJava()
    }

    js(IR) {
        browser()
        nodejs {
            testTask {
                // xmlutil's JS backend requires browser DOMParser, absent in Node.js
                filter.excludeTestsMatching("com.helpchoice.nahal.haldish.parser.XmlHalParserTest")
            }
        }
        binaries.library()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    linuxX64   { binaries { sharedLib { baseName = "haldish" } } }
    linuxArm64()

    macosX64   { binaries { sharedLib { baseName = "haldish" } } }
    macosArm64 { binaries { sharedLib { baseName = "haldish" } } }

    mingwX64   { binaries { sharedLib { baseName = "haldish" } } }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Intermediate source set shared by nativeMain and wasmJsMain.
        // Holds expect/actual stubs for APIs unavailable on those platforms (e.g. YAML via kaml).
        val nonJvmNonJsMain by creating {
            dependsOn(commonMain.get())
        }
        nativeMain.get().dependsOn(nonJvmNonJsMain)
        val wasmJsMain by getting {
            dependsOn(nonJvmNonJsMain)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.kaml)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kaml)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        linuxMain.dependencies {
            implementation(libs.ktor.client.curl)
        }

        mingwMain.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
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

        // Direct-child element helpers
        fun Element.children(tag: String): List<Element> =
            (0 until childNodes.length)
                .map { childNodes.item(it) }
                .filterIsInstance<Element>()
                .filter { tagName == tag || it.tagName == tag }
                .filter { it.parentNode == this }

        data class Counter(val covered: Int, val total: Int) {
            val missed get() = total - covered
            val pct    get() = if (total == 0) 100.0 else covered * 100.0 / total
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
        println("║${"Coverage Report".padStart(38).padEnd(58)}║")
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
        val labelWidth = packages.maxOf { pkg ->
            pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/haldish/")
                .replace('/', '.')
                .ifEmpty { "(root)" }.length
        }.coerceAtLeast(7)

        packages.forEach { pkg ->
            val name = pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/haldish/")
                .replace('/', '.')
                .ifEmpty { "(root)" }
            pkg.counters()["LINE"]?.let { println(row(name, it, labelWidth)) }
        }

        println()
        println("BY CLASS  (line coverage)")

        val classLabelWidth = packages.flatMap { it.children("class") }.maxOf { cls ->
            cls.getAttribute("name").substringAfterLast('/').length
        }.coerceAtLeast(7)

        packages.forEach { pkg ->
            val pkgLabel = pkg.getAttribute("name")
                .removePrefix("com/helpchoice/nahal/haldish/")
                .replace('/', '.')
                .ifEmpty { "(root)" }
            val classes = pkg.children("class").sortedBy { it.getAttribute("name") }
            classes.forEach { cls ->
                val clsName = cls.getAttribute("name").substringAfterLast('/')
                cls.counters()["LINE"]?.let {
                    println(row("$pkgLabel/$clsName", it, classLabelWidth + pkgLabel.length + 1))
                }
            }
        }
        println()
    }
}

// ── Platform example tasks ────────────────────────────────────────────────────

tasks.register<Exec>("runJsSimpleExample") {
    group = "verification"
    description = "Runs the no-HTTP JavaScript example against the compiled JS library"
    dependsOn("jsProductionLibraryCompileSync")

    // Use the Node.js binary that Kotlin's JS toolchain downloads — it's always present
    // after jsProductionLibraryCompileSync runs and doesn't depend on PATH or nvm.
    val nodeDir = rootProject.layout.projectDirectory
        .dir("../.gradle/nodejs")
        .asFile
        .walkTopDown()
        .firstOrNull { it.name == "node" && it.parentFile.name == "bin" && it.canExecute() }
        ?: file(System.getProperty("user.home") + "/.nvm/versions/node/v24.15.0/bin/node")

    commandLine(nodeDir, "examples/javascript/simple-example.cjs")
    workingDir = rootProject.projectDir
}

tasks.register<Exec>("compileNativeSimpleExample") {
    group       = "verification"
    description = "Compiles the C++ simple example against the macosX64 shared library"
    dependsOn("linkReleaseSharedMacosX64")
    val outDir  = layout.buildDirectory.dir("bin/macosX64/releaseShared").get().asFile
    commandLine(
        "g++", "-std=c++17",
        "-o", temporaryDir.resolve("haldish_simple_example").absolutePath,
        "src/examples/cpp/simple_example.cpp",
        "-I${outDir.absolutePath}", "-L${outDir.absolutePath}",
        "-lhaldish", "-Wl,-rpath,${outDir.absolutePath}",
    )
    workingDir = projectDir
}

tasks.register<Exec>("runNativeSimpleExample") {
    group       = "verification"
    description = "Compiles and runs the no-HTTP C++ simple example"
    dependsOn("compileNativeSimpleExample")
    commandLine(
        tasks.named<Exec>("compileNativeSimpleExample").get()
            .temporaryDir.resolve("haldish_simple_example").absolutePath
    )
}

// ── Maven publishing ──────────────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "haldish",
        version    = project.version.toString(),
    )

    pom {
        name        = "Haldish"
        description = "Kotlin Multiplatform HAL client library — HTTP, HAL parsing, URI templates"
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
