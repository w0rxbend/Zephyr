package com.worxbend.zephyr

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun isSystemDarkMode(): Boolean {
    val gtkTheme = System.getenv("GTK_THEME")
    if (!gtkTheme.isNullOrBlank() && gtkTheme.contains(":dark", ignoreCase = true)) return true
    return try {
        val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
            .redirectErrorStream(true)
            .start()
        val result = process.inputStream.bufferedReader().readText()
        process.waitFor()
        "dark" in result.lowercase()
    } catch (_: Exception) {
        false
    }
}