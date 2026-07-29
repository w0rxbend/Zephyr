package com.worxbend.zephyr.settings

import com.worxbend.zephyr.domain.InstallTarget

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.System,
    val uiDensity: UiDensity = UiDensity.Compact,
    val showSdkmanHome: Boolean = true,
    val favoriteCandidates: Set<String> = emptySet(),
    val favoriteJdkVendors: Set<String> = emptySet(),
    val recentCandidates: List<String> = emptyList(),
    val toolchainProfiles: List<ToolchainProfile> = emptyList(),
    val navigationWidthDp: Int = 0,
)

const val MIN_NAVIGATION_WIDTH_DP = 190
const val MAX_NAVIGATION_WIDTH_DP = 360

fun Int.normalizedNavigationWidth(): Int =
    if (this == 0) 0 else coerceIn(MIN_NAVIGATION_WIDTH_DP, MAX_NAVIGATION_WIDTH_DP)

data class ToolchainProfile(
    val name: String,
    val targets: List<InstallTarget>,
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
