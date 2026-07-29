package com.worxbend.zephyr

import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind

internal data class CandidateUpdate(
    val candidate: String,
    val displayName: String,
    val kind: CandidateKind,
    val currentVersion: String?,
    val targetVersion: String,
)

internal fun availableCandidateUpdates(
    candidates: List<Candidate>,
    catalog: List<CandidateCatalogItem>,
): List<CandidateUpdate> {
    val catalogByName = catalog.associateBy(CandidateCatalogItem::name)
    return candidates.mapNotNull { candidate ->
        val target = catalogByName[candidate.name]?.stableVersion?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val installed = candidate.installedVersions
            .asSequence()
            .filter { it.isInstalled }
            .map { it.version }
            .toSet()
        if (target in installed) return@mapNotNull null
        CandidateUpdate(
            candidate = candidate.name,
            displayName = candidate.displayName,
            kind = candidate.kind,
            currentVersion = candidate.defaultVersion ?: installed.firstOrNull(),
            targetVersion = target,
        )
    }.sortedWith(
        compareBy<CandidateUpdate> { it.kind.ordinal }
            .thenBy { it.displayName.lowercase() },
    )
}
