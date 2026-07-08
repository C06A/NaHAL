package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.helpchoice.nahal.ui.component.LocalFilePicker
import com.helpchoice.nahal.ui.component.PickedFile
import com.helpchoice.nahal.ui.component.guessContentType
import javax.swing.JFileChooser

fun main() {
    System.setProperty("apple.awt.application.name", "NaHAL")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "NaHAL",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            CompositionLocalProvider(
                LocalFilePicker provides { callback ->
                    val dialog = JFileChooser()
                    callback(
                        if (dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val file = dialog.selectedFile
                            val bytes = file.readBytes()
                            PickedFile(file.name, bytes.decodeToString(), bytes, guessContentType(file.name))
                        } else null
                    )
                }
            ) {
                NaHalNavigator()
            }
        }
    }
}
