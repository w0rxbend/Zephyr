package com.worxbend.zephyr.settings

import com.worxbend.zephyr.domain.InstallTarget

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.System,
    val uiDensity: UiDensity = UiDensity.Compact,
    val textScale: TextScale = TextScale.Percent100,
    val motionPreference: MotionPreference = MotionPreference.System,
    val metadataRefreshSchedule: MetadataRefreshSchedule = MetadataRefreshSchedule.Off,
    val showSdkmanHome: Boolean = true,
    val favoriteCandidates: Set<String> = emptySet(),
    val favoriteJdkVendors: Set<String> = emptySet(),
    val recentCandidates: List<String> = emptyList(),
    val toolchainProfiles: List<ToolchainProfile> = emptyList(),
    val navigationWidthDp: Int = 0,
    val installedViewMode: CollectionViewMode = CollectionViewMode.Cards,
    val catalogViewMode: CollectionViewMode = CollectionViewMode.Cards,
    val savedJdkFilters: List<SavedJdkFilter> = emptyList(),
)

data class SavedJdkFilter(
    val name: String,
    val query: String,
    val status: String,
    val providerCode: String?,
    val sort: String,
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

enum class TextScale(val label: String, val factor: Float) {
    Percent100("100%", 1f),
    Percent125("125%", 1.25f),
    Percent150("150%", 1.5f),
    Percent175("175%", 1.75f),
    Percent200("200%", 2f),
}

enum class MotionPreference(val label: String) {
    System("System"),
    Full("Full"),
    Reduced("Reduced"),
}

fun MotionPreference.reducesMotion(systemReducedMotion: Boolean): Boolean =
    when (this) {
        MotionPreference.System -> systemReducedMotion
        MotionPreference.Full -> false
        MotionPreference.Reduced -> true
    }

enum class MetadataRefreshSchedule(
    val label: String,
    val intervalMillis: Long?,
) {
    Off("Off", null),
    Hourly("Hourly", 60L * 60L * 1_000L),
    EverySixHours("Every 6 hours", 6L * 60L * 60L * 1_000L),
    Daily("Daily", 24L * 60L * 60L * 1_000L),
}

enum class CollectionViewMode(val label: String) {
    Cards("Cards"),
    Table("Table"),
}
