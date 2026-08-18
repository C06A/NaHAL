package com.helpchoice.nahal.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.helpchoice.nahal.ui.NaHalNavigator

/**
 * Android entry point. Like every other platform entry (`jvmMain/main.kt`, `jsMain/JsEntry.kt`,
 * `macosMain/MacosEntry.kt`, `iosMain/IOSEntry.kt`) this only hosts [NaHalNavigator] — all state
 * and navigation live in `:ui` and `:core`.
 *
 * The app ships plugin-free, matching every other target: nothing activates unless a config
 * source names it. On Android that means `HALDISH_CONFIG` as a system property, since an app
 * cannot set its own environment variables.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NaHalNavigator()
        }
    }
}
