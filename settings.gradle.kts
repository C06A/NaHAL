rootProject.name = "nahal"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // haldish now lives in its own build (../HALDiSh_KMP) and is consumed as a published
        // artifact. Until its 2.0.0 is on Central (1.0.1 is the newest published there), run
        // `./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false` in that project and
        // this repository resolves it from the local Maven repository.
        mavenLocal()
    }
}

include(":core")
include(":ui")
include(":testkit")
include(":testkit-groovy")

include(":plugins:api-key")
include(":plugins:chain")
include(":plugins:curie")
include(":plugins:bearer-token")
include(":plugins:base-url-rewriter")
include(":plugins:logger")
