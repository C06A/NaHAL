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
        // haldish lives in its own build (../HALDiSh_KMP) and is consumed here purely as a
        // published artifact — Central serves the version this build asks for, so no local
        // publishing step stands between cloning and building.
        mavenCentral()
    }
}

include(":core")
include(":ui")
include(":androidApp")
include(":testkit")
include(":testkit-groovy")

