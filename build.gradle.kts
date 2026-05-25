plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.vanniktech.publish)    apply false
}

allprojects {
    group   = "com.helpchoice.nahal"
    version = "0.1.0-SNAPSHOT"
}
