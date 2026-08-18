import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// The installable Android app. It is deliberately NOT published to Maven Central — :ui carries the
// reusable `nahal-ui` library (with an android variant), this module only wraps it in an Activity.

android {
    namespace  = "com.helpchoice.nahal.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.helpchoice.nahal.android"
        minSdk        = libs.versions.android.minSdk.get().toInt()
        targetSdk     = libs.versions.android.compileSdk.get().toInt()
        // Android requires a monotonically increasing integer; derive it from the semver so it
        // tracks the project version without a second thing to remember to bump.
        //   2.0.1 -> 2_000_001
        versionCode = version.toString().substringBefore('-').split('.').let { p ->
            p.getOrNull(0)?.toIntOrNull().orZero() * 1_000_000 +
            p.getOrNull(1)?.toIntOrNull().orZero() * 1_000 +
            p.getOrNull(2)?.toIntOrNull().orZero()
        }
        versionName = version.toString()
    }

    // Release signing. Supply a real key through gradle properties or the matching env vars:
    //   NAHAL_KEYSTORE, NAHAL_KEYSTORE_PASSWORD, NAHAL_KEY_ALIAS, NAHAL_KEY_PASSWORD
    // Without them the release build is signed with the local debug key so the APK still
    // installs — fine for a GitHub download, NOT acceptable for a Play Store upload.
    val keystorePath = providers.gradleProperty("NAHAL_KEYSTORE")
        .orElse(providers.environmentVariable("NAHAL_KEYSTORE"))
        .orNull

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile     = file(keystorePath)
                storePassword = secret("NAHAL_KEYSTORE_PASSWORD")
                keyAlias      = secret("NAHAL_KEY_ALIAS")
                keyPassword   = secret("NAHAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig =
                if (keystorePath != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Kotlin defaults to the toolchain JDK (21 here) while AGP's javac is pinned to 11 above; the two
// have to agree or the Kotlin plugin fails the build.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Warn once, at configuration time, rather than letting an unsigned-looking release slip by.
if (providers.gradleProperty("NAHAL_KEYSTORE")
        .orElse(providers.environmentVariable("NAHAL_KEYSTORE")).orNull == null) {
    logger.lifecycle(
        "androidApp: no NAHAL_KEYSTORE configured — release builds will use the debug signing key."
    )
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.runtime)
    implementation(compose.ui)
}

fun Int?.orZero(): Int = this ?: 0

/** Reads [name] from gradle properties, then the environment. */
fun secret(name: String): String =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
        ?: error("$name must be set when NAHAL_KEYSTORE is configured")
