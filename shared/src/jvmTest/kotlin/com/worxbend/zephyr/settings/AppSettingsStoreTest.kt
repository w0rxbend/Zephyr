package com.worxbend.zephyr.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.worxbend.zephyr.domain.InstallTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStoreTest {
    @Test
    fun loadsAndPersistsSettingsUpdates() = runBlocking {
        val initial = AppSettings(
            themePreference = ThemePreference.Dark,
            uiDensity = UiDensity.Comfortable,
            textScale = TextScale.Percent150,
            motionPreference = MotionPreference.Reduced,
            metadataRefreshSchedule = MetadataRefreshSchedule.EverySixHours,
            showSdkmanHome = false,
            favoriteCandidates = setOf("gradle", "kotlin"),
            favoriteJdkVendors = setOf("tem", "zulu"),
            recentCandidates = listOf("kotlin", "gradle"),
            toolchainProfiles = listOf(
                ToolchainProfile("Backend", listOf(InstallTarget("java", "21.0.5-tem"))),
            ),
            navigationWidthDp = 286,
            installedViewMode = CollectionViewMode.Table,
            catalogViewMode = CollectionViewMode.Table,
            savedJdkFilters = listOf(
                SavedJdkFilter("Temurin", "", "Available", "tem", "Version"),
            ),
        )
        val saved = CompletableDeferred<AppSettings>()
        val repository = FakeAppSettingsRepository(initial) { saved.complete(it) }
        val store = AppSettingsStore(repository, Dispatchers.Unconfined)

        assertEquals(initial, withTimeout(1_000) { store.state.first { it == initial } })

        val updated = initial.copy(themePreference = ThemePreference.Light)
        store.update { it.copy(themePreference = ThemePreference.Light) }

        assertEquals(updated, withTimeout(1_000) { saved.await() })
        assertEquals(updated, store.state.value)
        store.close()
    }

    @Test
    fun jvmRepositoryPersistsFavoriteSets() = runBlocking {
        val preferences = java.util.prefs.Preferences.userRoot()
            .node("/com/worxbend/zephyr/tests/favorites-${System.nanoTime()}")
        try {
            val repository = JvmAppSettingsRepository(preferences)
            val expected = AppSettings(
                favoriteCandidates = setOf("kotlin", "gradle"),
                favoriteJdkVendors = setOf("zulu", "tem"),
                textScale = TextScale.Percent200,
                motionPreference = MotionPreference.Full,
                metadataRefreshSchedule = MetadataRefreshSchedule.Daily,
                recentCandidates = listOf("kotlin", "gradle"),
                toolchainProfiles = listOf(
                    ToolchainProfile(
                        "Backend | JVM",
                        listOf(
                            InstallTarget("java", "21.0.5-tem"),
                            InstallTarget("gradle", "8.14"),
                        ),
                    ),
                ),
                navigationWidthDp = 320,
                installedViewMode = CollectionViewMode.Table,
                catalogViewMode = CollectionViewMode.Table,
                savedJdkFilters = listOf(
                    SavedJdkFilter("Local Zulu", "17", "LocalOnly", "zulu", "Vendor"),
                ),
            )

            repository.save(expected)

            assertEquals(expected, repository.load())
        } finally {
            preferences.removeNode()
            preferences.flush()
        }
    }

    @Test
    fun recentCandidatesAreDeduplicatedAndBounded() {
        val settings = AppSettings(recentCandidates = listOf("gradle", "kotlin", "maven"))

        val updated = settings.recordRecentCandidate("kotlin", limit = 3)

        assertEquals(listOf("kotlin", "gradle", "maven"), updated.recentCandidates)
    }

    @Test
    fun navigationWidthIsClampedToSafeBounds() {
        assertEquals(MIN_NAVIGATION_WIDTH_DP, 100.normalizedNavigationWidth())
        assertEquals(MAX_NAVIGATION_WIDTH_DP, 500.normalizedNavigationWidth())
        assertEquals(0, 0.normalizedNavigationWidth())
    }

    @Test
    fun motionPreferenceHonorsSystemAndExplicitOverrides() {
        assertEquals(true, MotionPreference.System.reducesMotion(systemReducedMotion = true))
        assertEquals(false, MotionPreference.System.reducesMotion(systemReducedMotion = false))
        assertEquals(false, MotionPreference.Full.reducesMotion(systemReducedMotion = true))
        assertEquals(true, MotionPreference.Reduced.reducesMotion(systemReducedMotion = false))
    }

    @Test
    fun metadataRefreshSchedulesAreOptInAndBounded() {
        assertEquals(null, MetadataRefreshSchedule.Off.intervalMillis)
        assertEquals(3_600_000L, MetadataRefreshSchedule.Hourly.intervalMillis)
        assertEquals(21_600_000L, MetadataRefreshSchedule.EverySixHours.intervalMillis)
        assertEquals(86_400_000L, MetadataRefreshSchedule.Daily.intervalMillis)
    }
}

private class FakeAppSettingsRepository(
    private val initial: AppSettings,
    private val onSave: (AppSettings) -> Unit,
) : AppSettingsRepository {
    override suspend fun load(): AppSettings = initial

    override suspend fun save(settings: AppSettings) {
        onSave(settings)
    }
}
