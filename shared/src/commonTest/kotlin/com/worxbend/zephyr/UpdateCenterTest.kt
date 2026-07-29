package com.worxbend.zephyr

import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateVersion
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
