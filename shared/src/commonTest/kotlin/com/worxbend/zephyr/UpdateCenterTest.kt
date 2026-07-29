package com.worxbend.zephyr

import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.settings.UpdateNotificationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateCenterTest {
    @Test
    fun findsCatalogStableVersionsThatAreNotInstalled() {
        val installed = candidate("gradle", "8.10")
        val catalog = catalog("gradle", "8.14")

        val update = availableCandidateUpdates(listOf(installed), listOf(catalog)).single()

        assertEquals("8.10", update.currentVersion)
        assertEquals("8.14", update.targetVersion)
    }

    @Test
    fun excludesCandidatesWhoseStableVersionIsAlreadyInstalled() {
        val installed = candidate("gradle", "8.14")
        val catalog = catalog("gradle", "8.14")

        assertEquals(emptyList(), availableCandidateUpdates(listOf(installed), listOf(catalog)))
    }

    @Test
    fun updateOnlyPolicyNotifiesWithoutOpeningBrowse() {
        val notification = updateNotification(
            policy = UpdateNotificationPolicy.UpdatesOnly,
            candidates = listOf(candidate("gradle", "8.10")),
            catalog = listOf(catalog("gradle", "8.14")),
        )

        assertEquals("1 toolchain update available", notification?.title)
        assertEquals("Gradle 8.14", notification?.message)
        assertEquals("gradle:8.14", notification?.signature)
    }

    @Test
    fun notificationPolicyControlsCurrentAndDisabledChecks() {
        val candidates = listOf(candidate("gradle", "8.14"))
        val catalog = listOf(catalog("gradle", "8.14"))

        assertEquals(
            null,
            updateNotification(UpdateNotificationPolicy.Off, candidates, catalog),
        )
        assertEquals(
            null,
            updateNotification(UpdateNotificationPolicy.UpdatesOnly, candidates, catalog),
        )
        assertEquals(
            "Your loaded SDKMAN toolchain is current.",
            updateNotification(UpdateNotificationPolicy.AllChecks, candidates, catalog)?.message,
        )
    }

    private fun candidate(name: String, version: String) = Candidate(
        name = name,
        displayName = name.replaceFirstChar { it.titlecase() },
        kind = CandidateKind.Sdk,
        installedVersions = listOf(CandidateVersion(version, true, true, true)),
        defaultVersion = version,
        hasLocalOnlyVersions = false,
        localOnlyVersionCount = 0,
        localOnlyVersions = emptyList(),
    )

    private fun catalog(name: String, stableVersion: String) = CandidateCatalogItem(
        name = name,
        displayName = name.replaceFirstChar { it.titlecase() },
        stableVersion = stableVersion,
        description = null,
        websiteUrl = null,
        kind = CandidateKind.Sdk,
        isInstalled = true,
    )
}
