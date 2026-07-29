package com.worxbend.zephyr

import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.settings.UpdateNotificationPolicy

internal data class CandidateUpdate(
    val candidate: String,
    val displayName: String,
    val kind: CandidateKind,
    val currentVersion: String?,
    val targetVersion: String,
)

internal data class UpdateNotification(
    val title: String,
    val message: String,
    val signature: String,
)

internal fun updateNotification(
    policy: UpdateNotificationPolicy,
    candidates: List<Candidate>,
    catalog: List<CandidateCatalogItem>,
): UpdateNotification? {
    if (policy == UpdateNotificationPolicy.Off || catalog.isEmpty()) return null
    val updates = availableCandidateUpdates(candidates, catalog)
    val signature = updates.joinToString("|") { "${it.candidate}:${it.targetVersion}" }
        .ifEmpty { "current" }
    if (updates.isEmpty()) {
        return if (policy == UpdateNotificationPolicy.AllChecks) {
            UpdateNotification(
                title = "Zephyr update check",
                message = "Your loaded SDKMAN toolchain is current.",
                signature = signature,
            )
        } else {
            null
        }
    }
    return UpdateNotification(
        title = if (updates.size == 1) "1 toolchain update available" else "${updates.size} toolchain updates available",
        message = updates
            .take(3)
            .joinToString(", ") { "${it.displayName} ${it.targetVersion}" }
            .let { summary ->
                if (updates.size > 3) "$summary, and ${updates.size - 3} more" else summary
            },
        signature = signature,
    )
}

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
