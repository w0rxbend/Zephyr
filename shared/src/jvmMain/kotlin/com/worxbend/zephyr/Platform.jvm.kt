package com.worxbend.zephyr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

actual suspend fun isSystemDarkMode(): Boolean = withContext(Dispatchers.IO) {
    detectSystemDarkMode()
}

private fun detectSystemDarkMode(): Boolean {
    val gtkTheme = System.getenv("GTK_THEME")
    if (!gtkTheme.isNullOrBlank() && gtkTheme.contains(":dark", ignoreCase = true)) return true
    val gsettings = File("/usr/bin/gsettings")
    if (!gsettings.canExecute()) return false

    return try {
        val process = ProcessBuilder(gsettings.absolutePath, "get", "org.gnome.desktop.interface", "color-scheme")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(GSETTINGS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            false
        } else {
            process.inputStream.bufferedReader().use { reader ->
                "dark" in reader.readText().lowercase()
            }
        }
    } catch (_: Exception) {
        false
    }
}

private const val GSETTINGS_TIMEOUT_MILLIS = 250L
