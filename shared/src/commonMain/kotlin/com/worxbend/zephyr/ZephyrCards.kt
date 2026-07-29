package com.worxbend.zephyr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.JavaVersion
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.javaProviderName

@Composable
internal fun CandidateGrid(
    candidates: List<Candidate>,
    protectedVersions: Set<ProtectedVersion> = emptySet(),
    onOpen: (Candidate) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val spacing = LocalZephyrMetrics.current.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(250.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(candidates, key = { it.name }) { candidate ->
            val protectedLocalOnly = candidate.localOnlyVersions.filter { version ->
                ProtectedVersion(candidate.name, version) in protectedVersions
            }
            CandidateCard(
                candidate = candidate,
                protectedLocalOnlyCount = protectedLocalOnly.size,
                onClick = { onOpen(candidate) },
                onClean = {
                    onClean(candidate.name, candidate.localOnlyVersions - protectedLocalOnly.toSet())
                },
            )
        }
    }
}

@Composable
internal fun CandidateCard(
    candidate: Candidate,
    protectedLocalOnlyCount: Int,
    onClick: () -> Unit,
    onClean: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    ZephyrClickablePanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (metrics.controlHeight <= 32.dp) 174.dp else 194.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(metrics.spacing)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CandidateIcon(candidate.kind)
                Column(Modifier.weight(1f)) {
                    Text(candidate.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SDKMAN key: ${candidate.name}", style = MaterialTheme.typography.bodySmall)
                }
                CopyTextButton(candidate.name, "Copy key")
            }
            LinkText(
                text = candidate.description ?: "${candidate.installedVersions.count { it.isInstalled }} installed version(s)",
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
            )
            Spacer(Modifier.weight(1f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidate.defaultVersion?.let { Badge("Default: $it", BadgeTone.Primary) }
                if (candidate.hasLocalOnlyVersions) Badge("${candidate.localOnlyVersionCount} local-only", BadgeTone.Warning)
                if (protectedLocalOnlyCount > 0) Badge("$protectedLocalOnlyCount protected", BadgeTone.Primary)
            }
            if (candidate.hasLocalOnlyVersions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (protectedLocalOnlyCount < candidate.localOnlyVersionCount) {
                        OutlinedButton(onClick = onClean, modifier = Modifier.height(34.dp)) {
                            Text("Clean unprotected", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            "All local-only versions are protected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PackageCard(
    item: CandidateCatalogItem,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    ZephyrClickablePanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (metrics.controlHeight <= 32.dp) 164.dp else 184.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(metrics.spacing)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CandidateIcon(item.kind)
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SDKMAN key: ${item.name}", style = MaterialTheme.typography.bodySmall)
                }
                CopyTextButton(item.name, "Copy key")
            }
            LinkText(item.description ?: "Available from SDKMAN.", Modifier.weight(1f, fill = false), maxLines = 2)
            Spacer(Modifier.weight(1f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.stableVersion?.let { Badge("Stable: $it", BadgeTone.Success) }
                if (item.isInstalled) Badge("Installed", BadgeTone.Primary)
                if (isFavorite) Badge("Favorite", BadgeTone.Primary)
            }
            if (onToggleFavorite != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.height(34.dp)) {
                        Text(if (isFavorite) "★ Favorited" else "☆ Favorite")
                    }
                }
            }
        }
    }
}

@Composable
internal fun JdkVersionCard(
    version: JavaVersion,
    default: String?,
    isProtected: Boolean,
    onToggleProtected: () -> Unit,
    onClean: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    ZephyrPanel {
        Row(
            Modifier.fillMaxWidth().padding(metrics.panelPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CandidateIcon(CandidateKind.Jdk)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("JDK ${version.featureVersion}", fontWeight = FontWeight.SemiBold)
                Text(version.identifier)
                Text(version.providerName ?: javaProviderName(version.providerCode) ?: "Provider unknown", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("SDKMAN key: java")
                    if (version.identifier == default) Badge("Default", BadgeTone.Primary)
                    if (!version.isRemoteAvailable) Badge("Local only", BadgeTone.Warning)
                    if (isProtected) Badge("Protected", BadgeTone.Primary)
                }
            }
            CopyTextButton(version.identifier, "Copy version")
            OutlinedButton(onClick = onToggleProtected) { Text(if (isProtected) "Unpin" else "Protect") }
            if (!version.isRemoteAvailable && version.identifier != default && !isProtected) {
                OutlinedButton(onClick = onClean) { Text("Clean") }
            }
            if (!version.isRemoteAvailable && version.identifier == default) {
                Text("Choose another default first", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
