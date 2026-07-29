package com.worxbend.zephyr.settings

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.System,
    val uiDensity: UiDensity = UiDensity.Compact,
    val showSdkmanHome: Boolean = true,
    val favoriteCandidates: Set<String> = emptySet(),
    val favoriteJdkVendors: Set<String> = emptySet(),
    val recentCandidates: List<String> = emptyList(),
)

fun AppSettings.recordRecentCandidate(candidate: String, limit: Int = 6): AppSettings {
    val normalized = candidate.trim()
    if (normalized.isEmpty()) return this
    return copy(
        recentCandidates = (listOf(normalized) + recentCandidates)
            .distinct()
            .take(limit.coerceAtLeast(1)),
    )
}

enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class UiDensity(val label: String) {
    Compact("Compact"),
    Comfortable("Comfortable"),
}
