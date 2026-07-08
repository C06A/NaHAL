import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    groovy
    alias(libs.plugins.vanniktech.publish)
}

java {
    // Match the Java 21 bytecode the rest of the project emits.
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    // The Kotlin core; api so Groovy callers also see haldish + testkit types.
    api(project(":testkit"))
    implementation(libs.groovy)

    // Spock suite reuses the scripted MockEngine API from :testkit's test fixtures.
    testImplementation(libs.spock.core)
    testImplementation(testFixtures(project(":testkit")))
}

tasks.test {
    useJUnitPlatform()
}

// ── integrationTest: gated Spock suite against a live HAL server; separate from unit `test` ──

val itSources = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}

configurations["integrationTestImplementation"].extendsFrom(
    configurations.implementation.get(),
    configurations.api.get(),
)
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    "integrationTestImplementation"(libs.spock.core)
    "integrationTestImplementation"(testFixtures(project(":testkit")))  // ItConfig + seeding
}

// Default the fixtures folder to the sibling MockingHAL checkout, referenced relative to the
// project root; override with -Dhaldish.it.fixtures=... . The server URL gates execution.
val itFixturesDefault = rootDir.toPath()
    .resolve("../MockingHAL/mockinghal/src/test/resources/haldish").normalize().toString()

val integrationTest by tasks.registering(Test::class) {
    description = "Runs Groovy/Spock integration tests against a configured HAL server (skips when unconfigured)."
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
        artifactId = "haldish-testkit-groovy",
        version    = project.version.toString(),
    )

    pom {
        name        = "HALDiSh TestKit — Groovy DSL"
        description = "Groovy/Spock DSL facade over HALDiSh TestKit — dynamic property, embedded, " +
            "and method access for readable HAL tests"
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
