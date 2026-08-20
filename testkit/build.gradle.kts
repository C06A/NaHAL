
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
    alias(libs.plugins.vanniktech.publish)
}

// :core publishes nothing, so depending on it as a project would leave an unresolvable
// `com.helpchoice.nahal:nahal-core` coordinate in haldish-testkit's POM. Only one class is used
// from it — the `curies` documentation resolver — so it is compiled in directly. Copied rather
// than srcDir'd on the whole package: the rest of core's commonMain needs `expect` platform
// support this JVM-only module has no actuals for.
val copyCoreDocLinkResolver by tasks.registering(Copy::class) {
    description = "Copies :core's DocLinkResolver into this module's sources — :core is not published."
    from("../core/src/commonMain/kotlin/com/helpchoice/nahal/core/DocLinkResolver.kt")
    into(layout.buildDirectory.dir("generated/core/com/helpchoice/nahal/core"))
}

kotlin {
    // Match the JDK the other modules compile with (they set no toolchain, so they emit
    // class-file version 65 / Java 21); the test JVM must be able to load them.
    jvmToolchain(21)

    sourceSets["main"].kotlin.srcDir(
        files(layout.buildDirectory.dir("generated/core")).builtBy(copyCoreDocLinkResolver)
    )
}

dependencies {
    // haldish is the wrapped client; re-exported so callers see HalLink/HalHttpResponse etc.
    api(libs.haldish)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)                              // YAML credential config files

    // Test fixtures: the scripted MockEngine API, shared with :testkit-groovy's Spock suite.
    testFixturesApi(libs.ktor.client.mock)

    // Tests: Kotlin (kotlin.test on the JUnit Platform).
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

// ── integrationTest: gated, runs against a live HAL server; kept out of the unit `test` task ──

val itSources = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["testFixtures"].output
    runtimeClasspath += sourceSets["main"].output + sourceSets["testFixtures"].output
}

configurations["integrationTestImplementation"].extendsFrom(
    configurations.implementation.get(),
    configurations.testFixturesApi.get(),
)
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    "integrationTestImplementation"(kotlin("test"))
    "integrationTestImplementation"(libs.junit.jupiter)
}

// Default the fixtures folder to the sibling MockingHAL checkout, referenced relative to the
// project root; override with -Dhaldish.it.fixtures=... . The server URL gates execution
// (unset → the suite skips), so it is only forwarded when supplied.
val itFixturesDefault = rootDir.toPath()
    .resolve("../MockingHAL/mockinghal/src/test/resources/haldish").normalize().toString()

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against a configured HAL server (skips when unconfigured)."
    group = "verification"
    testClassesDirs = itSources.output.classesDirs
    classpath = itSources.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    systemProperty("haldish.it.fixtures",
        System.getProperty("haldish.it.fixtures") ?: itFixturesDefault)
    System.getProperty("haldish.it.url")?.let { systemProperty("haldish.it.url", it) }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "haldish-testkit",
        version    = project.version.toString(),
    )

    pom {
        name        = "HALDiSh TestKit"
        description = "Readable HAL test wrapper over HALDiSh — express tests as a sequence of " +
            "HTTP calls, with sessions and body coercion (Kotlin core)"
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
        scm { url = "https://github.com/C06A/NaHAL" }
    }
}
