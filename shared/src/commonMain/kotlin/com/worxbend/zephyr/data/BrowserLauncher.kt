package com.worxbend.zephyr.data

interface BrowserLauncher {
    fun openHttps(url: String): Boolean
}

expect fun createBrowserLauncher(): BrowserLauncher

fun isValidHttpsUrl(url: String): Boolean {
    if (url.length !in 1..2_048 || !url.startsWith("https://")) return false
    if (url.any { it.isWhitespace() || it.code < 0x20 }) return false
    val authority = url.removePrefix("https://").substringBefore('/')
    if (authority.isBlank() || '@' in authority || ':' in authority) return false
    return authority.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) && '.' in authority
}
