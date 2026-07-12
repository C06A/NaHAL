plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.jvm)            apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.vanniktech.publish)    apply false
}

allprojects {
    group   = "com.helpchoice.nahal"
    version = "2.0.0"
}
