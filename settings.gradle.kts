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
    }
}

include(":haldish")
include(":core")
include(":ui")

include(":plugins:api-key")
include(":plugins:chain")
include(":plugins:bearer-token")
include(":plugins:base-url-rewriter")
include(":plugins:logger")
