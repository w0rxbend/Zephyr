package com.worxbend.zephyr.settings

import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmAppSettingsRepository(
    private val preferences: Preferences = Preferences.userNodeForPackage(JvmAppSettingsRepository::class.java),
) : AppSettingsRepository {
    override suspend fun load(): AppSettings = withContext(Dispatchers.IO) {
        AppSettings(
            themePreference = preferences.enumValue(THEME_KEY, ThemePreference.System),
            uiDensity = preferences.enumValue(DENSITY_KEY, UiDensity.Compact),
            showSdkmanHome = preferences.getBoolean(SHOW_SDKMAN_HOME_KEY, true),
            favoriteCandidates = preferences.stringSet(FAVORITE_CANDIDATES_KEY),
            favoriteJdkVendors = preferences.stringSet(FAVORITE_JDK_VENDORS_KEY),
            recentCandidates = preferences.stringList(RECENT_CANDIDATES_KEY),
        )
    }

    override suspend fun save(settings: AppSettings) = withContext(Dispatchers.IO) {
        preferences.put(THEME_KEY, settings.themePreference.name)
        preferences.put(DENSITY_KEY, settings.uiDensity.name)
        preferences.putBoolean(SHOW_SDKMAN_HOME_KEY, settings.showSdkmanHome)
        preferences.put(FAVORITE_CANDIDATES_KEY, settings.favoriteCandidates.encode())
        preferences.put(FAVORITE_JDK_VENDORS_KEY, settings.favoriteJdkVendors.encode())
        preferences.put(RECENT_CANDIDATES_KEY, settings.recentCandidates.encode())
        preferences.flush()
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(key: String, fallback: T): T =
        get(key, fallback.name)
            .let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: fallback

    private fun Preferences.stringSet(key: String): Set<String> =
        get(key, "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun Set<String>.encode(): String =
        asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .joinToString("\n")

    private fun Preferences.stringList(key: String): List<String> =
        get(key, "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()

    private fun List<String>.encode(): String =
        asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n")

    private companion object {
        const val THEME_KEY = "theme"
        const val DENSITY_KEY = "density"
        const val SHOW_SDKMAN_HOME_KEY = "show-sdkman-home"
        const val FAVORITE_CANDIDATES_KEY = "favorite-candidates"
        const val FAVORITE_JDK_VENDORS_KEY = "favorite-jdk-vendors"
        const val RECENT_CANDIDATES_KEY = "recent-candidates"
    }
}

actual fun createAppSettingsRepository(): AppSettingsRepository = JvmAppSettingsRepository()
