package com.worxbend.zephyr.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStoreTest {
    @Test
    fun loadsAndPersistsSettingsUpdates() = runBlocking {
        val initial = AppSettings(
            themePreference = ThemePreference.Dark,
            uiDensity = UiDensity.Comfortable,
            showSdkmanHome = false,
            favoriteCandidates = setOf("gradle", "kotlin"),
            favoriteJdkVendors = setOf("tem", "zulu"),
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
            )

            repository.save(expected)

            assertEquals(expected, repository.load())
        } finally {
            preferences.removeNode()
            preferences.flush()
        }
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
