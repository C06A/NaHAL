import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    // Match the JDK the other modules compile with (they set no toolchain, so they emit
    // class-file version 65 / Java 21); the test JVM must be able to load them.
    jvmToolchain(21)
}

dependencies {
    // haldish is the wrapped client; re-exported so callers see HalLink/HalHttpResponse etc.
    api(project(":haldish"))
    // Reuse the CURIE link-expansion logic and the curies documentation resolver.
    implementation(project(":plugins:curie"))
    implementation(project(":core"))
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId    = project.group.toString(),
        artifactId = "haldish-testkit",
        version    = project.version.toString(),
    )

    pom {
        name        = "HALDiSh TestKit"
        description = "Readable HAL test wrapper over HALDiSh — express tests as a sequence of " +
            "HTTP calls, with sessions, CURIE, and body coercion (Kotlin core)"
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
