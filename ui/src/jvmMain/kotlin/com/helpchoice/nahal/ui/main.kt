package com.helpchoice.nahal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.helpchoice.nahal.ui.component.ExternalOpener
import com.helpchoice.nahal.ui.component.LocalExternalOpener
import com.helpchoice.nahal.ui.component.LocalFilePicker
import com.helpchoice.nahal.ui.component.PickedFile
import com.helpchoice.nahal.ui.component.guessContentType
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit
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
                },
                LocalExternalOpener provides ExternalOpener(
                    appNameFor = ::defaultAppName,
                    open = { body ->
                        runCatching {
                            val file = File.createTempFile("nahal-", ".${body.extension}")
                                .apply { deleteOnExit() }
                            file.writeBytes(body.bytes)
                            if (Desktop.isDesktopSupported() &&
                                Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
                            ) {
                                Desktop.getDesktop().open(file)
                            } else {
                                // Linux toolkits without AWT Desktop support
                                ProcessBuilder("xdg-open", file.absolutePath).start()
                            }
                        }.isSuccess
                    },
                ),
            ) {
                NaHalNavigator()
            }
        }
    }
}

private val appNameCache = mutableMapOf<String, String?>()

/** Best-effort display name of the OS default application for [contentType]; null when unknowable. */
private fun defaultAppName(contentType: String?): String? {
    val mime = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
    return appNameCache.getOrPut(mime) {
        val os = System.getProperty("os.name").lowercase()
        runCatching {
            when {
                "linux" in os   -> linuxAppName(mime)
                "windows" in os -> windowsAppName(mime)
                else            -> null   // macOS: no stock CLI to query LaunchServices
            }
        }.getOrNull()
    }
}

private fun runCommand(vararg cmd: String): String? {
    val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    return process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
}

private fun linuxAppName(mime: String): String? {
    // "org.inkscape.Inkscape.desktop" -> "Inkscape"
    val desktopId = runCommand("xdg-mime", "query", "default", mime) ?: return null
    return desktopId.removeSuffix(".desktop").substringAfterLast('.').takeIf { it.isNotEmpty() }
}

private fun windowsAppName(mime: String): String? {
    val ext = com.helpchoice.nahal.ui.component.extensionFor(mime)
    // "assoc .svg" -> ".svg=svgfile", "ftype svgfile" -> "svgfile=C:\...\app.exe" %1
    val fileType = runCommand("cmd", "/c", "assoc", ".$ext")?.substringAfter('=') ?: return null
    val command = runCommand("cmd", "/c", "ftype", fileType)?.substringAfter('=') ?: return null
    val exe = Regex("""([^\\/:"]+)\.exe""", RegexOption.IGNORE_CASE).find(command)?.groupValues?.get(1)
    return exe?.replaceFirstChar { it.uppercase() }
}
