package com.worxbend.zephyr.settings

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.System,
    val uiDensity: UiDensity = UiDensity.Compact,
    val showSdkmanHome: Boolean = true,
    val favoriteCandidates: Set<String> = emptySet(),
    val favoriteJdkVendors: Set<String> = emptySet(),
)

enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class UiDensity(val label: String) {
    Compact("Compact"),
    Comfortable("Comfortable"),
}
