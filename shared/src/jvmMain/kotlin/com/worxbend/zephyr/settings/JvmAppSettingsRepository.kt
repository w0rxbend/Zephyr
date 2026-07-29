package com.worxbend.zephyr.settings

import com.worxbend.zephyr.domain.InstallTarget
import java.nio.charset.StandardCharsets
import java.util.Base64
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
            toolchainProfiles = preferences.profiles(PROFILES_KEY),
        )
    }

    override suspend fun save(settings: AppSettings) = withContext(Dispatchers.IO) {
        preferences.put(THEME_KEY, settings.themePreference.name)
        preferences.put(DENSITY_KEY, settings.uiDensity.name)
        preferences.putBoolean(SHOW_SDKMAN_HOME_KEY, settings.showSdkmanHome)
        preferences.put(FAVORITE_CANDIDATES_KEY, settings.favoriteCandidates.encode())
        preferences.put(FAVORITE_JDK_VENDORS_KEY, settings.favoriteJdkVendors.encode())
        preferences.put(RECENT_CANDIDATES_KEY, settings.recentCandidates.encode())
        preferences.put(PROFILES_KEY, settings.toolchainProfiles.encodeProfiles())
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

    private fun Preferences.profiles(key: String): List<ToolchainProfile> =
        get(key, "")
            .lineSequence()
            .mapNotNull { encoded ->
                runCatching {
                    val decoded = String(PROFILE_DECODER.decode(encoded), StandardCharsets.UTF_8)
                    val fields = decoded.split(PROFILE_FIELD_SEPARATOR)
                    val name = fields.firstOrNull()?.trim().orEmpty()
                    val targets = fields.drop(1).mapNotNull { target ->
                        val parts = target.split(TARGET_FIELD_SEPARATOR, limit = 2)
                        if (parts.size == 2 && parts.all(String::isNotBlank)) {
                            InstallTarget(parts[0], parts[1])
                        } else {
                            null
                        }
                    }
                    ToolchainProfile(name, targets).takeIf { name.isNotEmpty() && targets.isNotEmpty() }
                }.getOrNull()
            }
            .toList()

    private fun List<ToolchainProfile>.encodeProfiles(): String =
        asSequence()
            .filter { it.name.isNotBlank() && it.targets.isNotEmpty() }
            .map { profile ->
                buildList {
                    add(profile.name.trim())
                    addAll(profile.targets.map { "${it.candidate}$TARGET_FIELD_SEPARATOR${it.version}" })
                }.joinToString(PROFILE_FIELD_SEPARATOR.toString())
            }
            .map { PROFILE_ENCODER.encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }
            .joinToString("\n")

    private companion object {
        const val THEME_KEY = "theme"
        const val DENSITY_KEY = "density"
        const val SHOW_SDKMAN_HOME_KEY = "show-sdkman-home"
        const val FAVORITE_CANDIDATES_KEY = "favorite-candidates"
        const val FAVORITE_JDK_VENDORS_KEY = "favorite-jdk-vendors"
        const val RECENT_CANDIDATES_KEY = "recent-candidates"
        const val PROFILES_KEY = "toolchain-profiles"
        const val PROFILE_FIELD_SEPARATOR = '\u001F'
        const val TARGET_FIELD_SEPARATOR = '\u001E'
        val PROFILE_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val PROFILE_DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}

actual fun createAppSettingsRepository(): AppSettingsRepository = JvmAppSettingsRepository()
