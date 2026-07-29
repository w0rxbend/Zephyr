package com.worxbend.zephyr.data

import java.awt.Desktop
import java.net.URI

internal class JvmBrowserLauncher : BrowserLauncher {
    override fun openHttps(url: String): Boolean {
        if (!isValidHttpsUrl(url) || !Desktop.isDesktopSupported()) return false
        return runCatching {
            val uri = URI(url)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank())
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
            desktop.browse(uri)
            true
        }.getOrDefault(false)
    }
}

actual fun createBrowserLauncher(): BrowserLauncher = JvmBrowserLauncher()
