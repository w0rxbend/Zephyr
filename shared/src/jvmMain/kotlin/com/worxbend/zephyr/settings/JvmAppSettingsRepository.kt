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
        )
    }

    override suspend fun save(settings: AppSettings) = withContext(Dispatchers.IO) {
        preferences.put(THEME_KEY, settings.themePreference.name)
        preferences.put(DENSITY_KEY, settings.uiDensity.name)
        preferences.putBoolean(SHOW_SDKMAN_HOME_KEY, settings.showSdkmanHome)
        preferences.flush()
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(key: String, fallback: T): T =
        get(key, fallback.name)
            .let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: fallback

    private companion object {
        const val THEME_KEY = "theme"
        const val DENSITY_KEY = "density"
        const val SHOW_SDKMAN_HOME_KEY = "show-sdkman-home"
    }
}

actual fun createAppSettingsRepository(): AppSettingsRepository = JvmAppSettingsRepository()
