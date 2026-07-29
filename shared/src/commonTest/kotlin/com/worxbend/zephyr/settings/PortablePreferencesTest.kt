package com.worxbend.zephyr.settings

import com.worxbend.zephyr.domain.InstallTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class PortablePreferencesTest {
    @Test
    fun formatRoundTripsDeterministicallyAndApplyPreservesMachineState() {
        val source = AppSettings(
            themePreference = ThemePreference.Dark,
            favoriteCandidates = setOf("gradle", "kotlin"),
            toolchainProfiles = listOf(
                ToolchainProfile("Backend\tJVM", listOf(InstallTarget("java", "21-tem"))),
            ),
            savedJdkFilters = listOf(SavedJdkFilter("Local", "17\nlts", "Installed", "tem", "Version")),
            navigationWidthDp = 300,
        )
        val portable = source.portablePreferences()
        val rendered = renderPortablePreferences(portable)
        val parsed = parsePortablePreferences(rendered)

        assertEquals(portable, parsed)
        assertEquals(rendered, renderPortablePreferences(parsed))

        val machineState = AppSettings(
            localOnlyObservations = listOf(LocalOnlyObservation("java", "old-local", 123)),
        ).applyPortablePreferences(parsed)
        assertEquals(source.themePreference, machineState.themePreference)
        assertEquals(listOf(LocalOnlyObservation("java", "old-local", 123)), machineState.localOnlyObservations)
    }
}
