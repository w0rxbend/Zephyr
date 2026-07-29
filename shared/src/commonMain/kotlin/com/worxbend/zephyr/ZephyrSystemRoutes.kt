package com.worxbend.zephyr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.BatchItemStatus
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.ConnectivityState
import com.worxbend.zephyr.domain.IntegrityCheck
import com.worxbend.zephyr.domain.IntegrityStatus
import com.worxbend.zephyr.domain.InstallTarget
import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.RecoveryAction
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.javaProviderName
import com.worxbend.zephyr.domain.recoveryGuidance
import com.worxbend.zephyr.domain.searchOperationJournal
import com.worxbend.zephyr.data.formatLocalTimestamp
import com.worxbend.zephyr.settings.AppSettings
import com.worxbend.zephyr.settings.ThemePreference
import com.worxbend.zephyr.settings.UiDensity
import com.worxbend.zephyr.viewmodel.ZephyrRoute
import com.worxbend.zephyr.viewmodel.ZephyrUiState
import com.worxbend.zephyr.viewmodel.ZephyrViewModel

@Composable
internal fun OverviewScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    settings: AppSettings,
) {
    val metrics = LocalZephyrMetrics.current
    val jdk = state.candidates.firstOrNull { it.kind == CandidateKind.Jdk }
    val sdks = state.candidates.count { it.kind == CandidateKind.Sdk }
    val installedVersions = state.candidates.sumOf { candidate -> candidate.installedVersions.count { it.isInstalled } }
    val localOnly = state.candidates.sumOf { it.localOnlyVersionCount }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        PageTitle("Overview", "Your SDKMAN toolchain at a glance.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        ) {
            ZephyrMetricTile(
                label = "Default JDK",
                value = jdk?.defaultVersion ?: "Not set",
                detail = if (jdk == null) "Install a JDK to get started" else "${jdk.installedVersions.count { it.isInstalled }} installed",
                tone = if (jdk?.defaultVersion != null) StatusTone.Success else StatusTone.Warning,
                modifier = Modifier.weight(1f),
            )
            ZephyrMetricTile(
                label = "Installed SDKs",
                value = sdks.toString(),
                detail = "$installedVersions total versions",
                tone = StatusTone.Accent,
                modifier = Modifier.weight(1f),
            )
            ZephyrMetricTile(
                label = "Local-only",
                value = localOnly.toString(),
                detail = if (localOnly == 0) "No cleanup needed" else "Review before cleaning",
                tone = if (localOnly == 0) StatusTone.Success else StatusTone.Warning,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        ) {
            ZephyrPanel(Modifier.weight(1.25f).fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(metrics.panelPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PanelHeading("Quick actions", "Common SDKMAN workflows")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZephyrToolbarButton("Browse JDKs", onClick = { viewModel.navigate(ZephyrRoute.BrowseJdks) })
                        ZephyrToolbarButton("Browse SDKs", onClick = { viewModel.navigate(ZephyrRoute.BrowseSdks) })
                        ZephyrToolbarButton("Update Center", onClick = { viewModel.navigate(ZephyrRoute.UpdateCenter) })
                        ZephyrToolbarButton("Batch Uninstall", onClick = { viewModel.navigate(ZephyrRoute.BatchUninstall) })
                        ZephyrToolbarButton("Refresh local state", onClick = viewModel::refreshInstalled)
                        ZephyrToolbarButton("Scan local-only", onClick = viewModel::scanLocalOnly)
                    }
                    PanelHeading("Toolchain summary", "Persisted SDKMAN defaults")
                    KeyValueRow("SDKMAN", sdkmanVersionLabel(state))
                    KeyValueRow("Default JDK", jdk?.defaultVersion ?: "Not configured")
                    KeyValueRow("Candidates", state.candidates.size.toString())
                    KeyValueRow("Catalog", if (state.catalog.isEmpty()) "Not loaded" else "${state.catalog.size} packages")
                    PanelHeading("Recent items", "Last-viewed candidate details")
                    if (settings.recentCandidates.isEmpty()) {
                        Text(
                            "Candidate details you open will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            settings.recentCandidates.forEach { candidate ->
                                val installed = state.candidates.firstOrNull { it.name == candidate }
                                val remote = state.catalog.firstOrNull { it.name == candidate }
                                val label = installed?.displayName ?: remote?.displayName ?: displayNameFor(candidate)
                                val kind = installed?.kind ?: remote?.kind
                                ZephyrToolbarButton(
                                    label = label,
                                    onClick = {
                                        viewModel.navigate(
                                            if (candidate == "java" || kind == CandidateKind.Jdk) {
                                                ZephyrRoute.JdkDetail(candidate)
                                            } else {
                                                ZephyrRoute.SdkDetail(candidate)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            ZephyrPanel(Modifier.weight(0.75f).fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(metrics.panelPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PanelHeading("Environment health", "Read-only diagnostics")
                    HealthRow("SDKMAN detected", true)
                    HealthRow("SDKMAN service online", state.connectivityStatus.state == ConnectivityState.Online)
                    HealthRow("CLI version available", state.sdkmanStatus.cliVersion != null)
                    HealthRow("Default JDK configured", jdk?.defaultVersion != null)
                    HealthRow("No local-only versions", localOnly == 0)
                    HealthRow(
                        "SDKMAN integrity",
                        state.integrityChecks.none { it.status == IntegrityStatus.Failed },
                    )
                    ZephyrToolbarButton(
                        label = "Open diagnostics",
                        onClick = { viewModel.navigate(ZephyrRoute.Diagnostics) },
                    )
                    PanelHeading("Favorites", "Pinned SDKs and JDK vendors")
                    if (settings.favoriteCandidates.isEmpty() && settings.favoriteJdkVendors.isEmpty()) {
                        Text(
                            "Pin SDKs or JDK vendors from Browse to keep them close.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            settings.favoriteCandidates.sorted().forEach { candidate ->
                                val label = state.catalog.firstOrNull { it.name == candidate }?.displayName
                                    ?: state.candidates.firstOrNull { it.name == candidate }?.displayName
                                    ?: displayNameFor(candidate)
                                ZephyrToolbarButton(
                                    label = "★ $label",
                                    onClick = { viewModel.navigate(ZephyrRoute.SdkDetail(candidate)) },
                                )
                            }
                            settings.favoriteJdkVendors.sorted().forEach { vendor ->
                                ZephyrToolbarButton(
                                    label = "★ ${javaProviderName(vendor) ?: vendor}",
                                    onClick = { viewModel.navigate(ZephyrRoute.BrowseJdks) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdateCenterScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
) {
    val metrics = LocalZephyrMetrics.current
    val updates = availableCandidateUpdates(state.candidates, state.catalog)
    val updateIds = updates.map { "${it.candidate}:${it.targetVersion}" }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(updateIds) {
        selected = selected.intersect(updateIds.toSet())
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                PageTitle(
                    "Update Center",
                    "Stable SDKMAN targets that are not installed in your current toolchain.",
                )
            }
            ZephyrToolbarButton(
                label = "Refresh metadata",
                onClick = { viewModel.requestTransaction(SdkmanTransaction.RefreshMetadata) },
                enabled = !state.isRefreshing && !state.isCatalogLoading,
            )
        }

        when {
            state.isCatalogLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Text("Loading SDKMAN update metadata…")
                }
            }
            state.catalog.isEmpty() -> {
                EmptyState(
                    "Update metadata unavailable",
                    "Load the SDKMAN catalog to check installed candidates for stable updates.",
                    "Refresh metadata",
                ) {
                    viewModel.requestTransaction(SdkmanTransaction.RefreshMetadata)
                }
            }
            updates.isEmpty() -> {
                EmptyState(
                    "Toolchain is current",
                    "Every installed candidate with a stable catalog target already has that version installed.",
                    "Browse SDKs",
                ) {
                    viewModel.navigate(ZephyrRoute.BrowseSdks)
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZephyrToolbarButton(
                        label = if (selected.size == updates.size) "Clear selection" else "Select all",
                        onClick = {
                            selected = if (selected.size == updates.size) emptySet() else updateIds.toSet()
                        },
                    )
                    Text(
                        "${selected.size} selected • ${updates.size} update(s)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ZephyrToolbarButton(
                        label = "Review selected (${selected.size})",
                        onClick = {
                            val targets = updates
                                .filter { "${it.candidate}:${it.targetVersion}" in selected }
                                .map { InstallTarget(it.candidate, it.targetVersion) }
                            if (targets.isNotEmpty()) {
                                viewModel.requestTransaction(SdkmanTransaction.BatchInstall(targets))
                            }
                        },
                        enabled = selected.isNotEmpty(),
                    )
                }
                if (state.batchInstallProgress.isNotEmpty()) {
                    ZephyrPanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(metrics.panelPadding),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            PanelHeading("Batch progress", "Sequential install results")
                            state.batchInstallProgress.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Badge(item.status.label, when (item.status) {
                                        BatchItemStatus.Succeeded -> BadgeTone.Success
                                        BatchItemStatus.Failed -> BadgeTone.Warning
                                        BatchItemStatus.Running -> BadgeTone.Primary
                                        BatchItemStatus.Pending -> BadgeTone.Neutral
                                    })
                                    Text(
                                        "${item.target.candidate} ${item.target.version}",
                                        modifier = Modifier.weight(1f),
                                    )
                                    item.outcome?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(metrics.spacing),
                ) {
                    updates.groupBy { it.kind }.forEach { (kind, group) ->
                        item {
                            Text(
                                if (kind == CandidateKind.Jdk) "JDK updates" else "SDK updates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(group, key = { "${it.candidate}:${it.targetVersion}" }) { update ->
                            val id = "${update.candidate}:${update.targetVersion}"
                            ZephyrPanel(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(metrics.panelPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Checkbox(
                                        checked = id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + id else selected - id
                                        },
                                    )
                                    CandidateIcon(update.kind)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(update.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${update.currentVersion ?: "No default"} → ${update.targetVersion}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Badge("SDKMAN key: ${update.candidate}")
                                            Badge("Stable target", BadgeTone.Success)
                                        }
                                    }
                                    ZephyrToolbarButton(
                                        label = "Inspect",
                                        onClick = {
                                            viewModel.navigate(
                                                if (update.kind == CandidateKind.Jdk) {
                                                    ZephyrRoute.JdkDetail(update.candidate)
                                                } else {
                                                    ZephyrRoute.SdkDetail(update.candidate)
                                                },
                                            )
                                        },
                                    )
                                    ZephyrToolbarButton(
                                        label = "Review update",
                                        onClick = {
                                            viewModel.requestTransaction(
                                                SdkmanTransaction.Install(
                                                    update.candidate,
                                                    update.targetVersion,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BatchUninstallScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
) {
    val metrics = LocalZephyrMetrics.current
    val items = uninstallSelectionItems(state.candidates, state.protectedVersions)
    val eligible = items.filter { it.blockedReason == null }
    val eligibleIds = eligible.map { "${it.target.candidate}:${it.target.version}" }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(eligibleIds) {
        selected = selected.intersect(eligibleIds.toSet())
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        PageTitle(
            "Batch Uninstall",
            "Remove selected non-default, unprotected versions with one reviewed transaction.",
        )
        if (items.isEmpty()) {
            EmptyState(
                "No installed versions",
                "Install a JDK or SDK version before preparing an uninstall batch.",
                "Browse SDKs",
            ) {
                viewModel.navigate(ZephyrRoute.BrowseSdks)
            }
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZephyrToolbarButton(
                label = if (selected.size == eligible.size && eligible.isNotEmpty()) {
                    "Clear selection"
                } else {
                    "Select all eligible"
                },
                onClick = {
                    selected = if (selected.size == eligible.size) emptySet() else eligibleIds.toSet()
                },
                enabled = eligible.isNotEmpty(),
            )
            Text(
                "${selected.size} selected • ${items.size - eligible.size} excluded",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ZephyrDestructiveButton(
                label = "Review uninstall (${selected.size})",
                onClick = {
                    val targets = eligible
                        .filter { "${it.target.candidate}:${it.target.version}" in selected }
                        .map { it.target }
                    if (targets.isNotEmpty()) {
                        viewModel.requestTransaction(SdkmanTransaction.BatchUninstall(targets))
                    }
                },
                enabled = selected.isNotEmpty(),
            )
        }
        if (state.batchUninstallProgress.isNotEmpty()) {
            ZephyrPanel(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(metrics.panelPadding),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PanelHeading("Batch progress", "Sequential uninstall results")
                    state.batchUninstallProgress.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Badge(item.status.label, batchStatusTone(item.status))
                            Text(
                                "${item.target.candidate} ${item.target.version}",
                                modifier = Modifier.weight(1f),
                            )
                            item.outcome?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.spacing),
        ) {
            items.groupBy { it.displayName }.forEach { (candidate, versions) ->
                item {
                    Text(candidate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(versions, key = { "${it.target.candidate}:${it.target.version}" }) { item ->
                    val id = "${item.target.candidate}:${item.target.version}"
                    val enabled = item.blockedReason == null
                    ZephyrPanel(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(metrics.panelPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = id in selected,
                                enabled = enabled,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + id else selected - id
                                },
                            )
                            CandidateIcon(item.kind)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.target.version, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Badge("SDKMAN key: ${item.target.candidate}")
                                    item.blockedReason?.let { Badge(it, BadgeTone.Warning) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun batchStatusTone(status: BatchItemStatus): BadgeTone =
    when (status) {
        BatchItemStatus.Succeeded -> BadgeTone.Success
        BatchItemStatus.Failed -> BadgeTone.Warning
        BatchItemStatus.Running -> BadgeTone.Primary
        BatchItemStatus.Pending -> BadgeTone.Neutral
    }

@Composable
internal fun DiagnosticsScreen(
    state: ZephyrUiState.Ready,
    onRefreshIntegrity: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    PageTitle("Diagnostics", "Inspect the SDKMAN integration without changing your environment.")
                }
                ZephyrToolbarButton(
                    label = if (state.diagnosticsExportInProgress) "Exporting…" else "Export support bundle",
                    onClick = onExportDiagnostics,
                    enabled = !state.diagnosticsExportInProgress,
                )
            }
        }
        item {
            ZephyrPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelHeading("Installation", "Local SDKMAN environment")
                    DiagnosticRow("SDKMAN home", state.sdkmanStatus.home ?: "Unavailable", state.sdkmanStatus.home != null)
                    DiagnosticRow("CLI version", sdkmanVersionLabel(state), state.sdkmanStatus.cliVersion != null)
                    DiagnosticRow(
                        "SDKMAN service",
                        state.connectivityStatus.state.label,
                        state.connectivityStatus.state == ConnectivityState.Online,
                    )
                    DiagnosticRow("Installed candidates", state.candidates.size.toString(), true)
                    DiagnosticRow(
                        "Persisted default JDK",
                        state.candidates.firstOrNull { it.name == "java" }?.defaultVersion ?: "Not configured",
                        state.candidates.firstOrNull { it.name == "java" }?.defaultVersion != null,
                    )
                }
            }
        }
        item {
            ZephyrPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelHeading("Remote metadata", "Catalog and updater state")
                    DiagnosticRow("Catalog packages", state.catalog.size.toString(), state.catalog.isNotEmpty())
                    DiagnosticRow("Metadata", metadataShortLabel(state.sdkmanStatus.metadataStatus), state.sdkmanStatus.metadataStatus !is CandidateMetadataStatus.Failed)
                    DiagnosticRow("SDKMAN update", selfUpdateShortLabel(state.sdkmanStatus.selfUpdateStatus), state.sdkmanStatus.selfUpdateStatus !is SdkmanSelfUpdateStatus.Failed)
                    DiagnosticRow(
                        "Local-only findings",
                        state.candidates.sumOf { it.localOnlyVersionCount }.toString(),
                        state.candidates.none { it.hasLocalOnlyVersions },
                    )
                }
            }
        }
        item {
            ZephyrPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            PanelHeading("Integrity checks", "Filesystem and SDKMAN runtime boundaries")
                        }
                        ZephyrToolbarButton("Run again", onClick = onRefreshIntegrity, enabled = !state.isRefreshing)
                    }
                    if (state.integrityChecks.isEmpty()) {
                        Text("Integrity checks are not available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.integrityChecks.forEach { check -> IntegrityCheckRow(check) }
                    }
                }
            }
        }
        item {
            Text(
                "Diagnostics are read-only. Refresh, update, cleanup, and repair actions remain explicit elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntegrityCheckRow(check: IntegrityCheck) {
    val tone = when (check.status) {
        IntegrityStatus.Passed -> StatusTone.Success
        IntegrityStatus.Warning -> StatusTone.Warning
        IntegrityStatus.Failed -> StatusTone.Error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(tone, Modifier.padding(top = 6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(check.title, fontWeight = FontWeight.Medium)
                Badge(check.status.label)
            }
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun OperationHistoryScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
) {
    val metrics = LocalZephyrMetrics.current
    var query by remember { mutableStateOf("") }
    val entries = state.operationJournal.searchOperationJournal(query)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        PageTitle(
            "Operation History",
            "Review confirmed SDKMAN mutations from this Zephyr session.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        ) {
            SearchField(query, { query = it }, "Search operations", Modifier.width(320.dp))
            Text(
                "${entries.size} of ${state.operationJournal.size}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ZephyrToolbarButton(
                label = if (state.journalExportInProgress) "Exporting…" else "Export CSV",
                onClick = viewModel::exportJournal,
                enabled = state.operationJournal.isNotEmpty() && !state.journalExportInProgress,
            )
        }
        when {
            state.operationJournal.isEmpty() -> EmptyState(
                title = "No operations yet",
                text = "Confirmed installs, default changes, removals, and maintenance actions will appear here.",
            )
            entries.isEmpty() -> EmptyState(
                title = "No matching operations",
                text = "No journal entries match \"$query\".",
                action = "Clear search",
                onAction = { query = "" },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(metrics.spacing),
            ) {
                items(entries, key = { it.id }) { entry ->
                    OperationJournalCard(
                        entry = entry,
                        onRecoveryAction = { action -> viewModel.handleRecoveryAction(entry, action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationJournalCard(
    entry: OperationJournalEntry,
    onRecoveryAction: (RecoveryAction) -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    val tone = when (entry.status) {
        OperationStatus.Running -> StatusTone.Accent
        OperationStatus.Succeeded -> StatusTone.Success
        OperationStatus.Failed -> StatusTone.Error
    }
    ZephyrPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(metrics.panelPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                StatusDot(tone)
                Text(
                    entry.transaction.title.removeSuffix("?"),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatLocalTimestamp(entry.startedAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Badge(entry.status.label)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entry.transaction.commands.forEach { command ->
                    Badge(command.action.label, BadgeTone.Primary)
                    command.candidate?.let { Badge(it) }
                    command.version?.let { Badge(it) }
                }
            }
            entry.outcome?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.status == OperationStatus.Failed) {
                val guidance = entry.transaction.recoveryGuidance()
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        guidance.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    guidance.steps.forEach { step ->
                        Text(
                            "• $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        guidance.actions.forEach { action ->
                            ZephyrToolbarButton(action.label, onClick = { onRecoveryAction(action) })
                        }
                    }
                }
            }
        }
    }
}

private fun ZephyrViewModel.handleRecoveryAction(
    entry: OperationJournalEntry,
    action: RecoveryAction,
) {
    when (action) {
        RecoveryAction.Retry -> retryTransaction(entry.transaction)
        RecoveryAction.RefreshInstalled -> refreshInstalled()
        RecoveryAction.RefreshMetadata -> requestTransaction(SdkmanTransaction.RefreshMetadata)
        RecoveryAction.ScanLocalOnly -> scanLocalOnly()
        RecoveryAction.OpenDiagnostics -> navigate(ZephyrRoute.Diagnostics)
    }
}

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 920.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        PageTitle("Settings", "Personalize Zephyr. Changes are saved for this desktop user.")
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.panelPadding)) {
                PanelHeading("Appearance", "Workbench colors and information density")
                ZephyrSettingsRow(
                    title = "Theme",
                    description = "Follow the Linux desktop or use an explicit light or dark theme.",
                ) {
                    ZephyrSegmentedControl(
                        options = ThemePreference.entries,
                        selected = settings.themePreference,
                        label = ThemePreference::label,
                        onSelected = { selected -> onSettingsChange { it.copy(themePreference = selected) } },
                    )
                }
                ZephyrSettingsRow(
                    title = "UI density",
                    description = "Compact fits more information; Comfortable adds spacing and larger controls.",
                ) {
                    ZephyrSegmentedControl(
                        options = UiDensity.entries,
                        selected = settings.uiDensity,
                        label = UiDensity::label,
                        onSelected = { selected -> onSettingsChange { it.copy(uiDensity = selected) } },
                    )
                }
            }
        }
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.panelPadding)) {
                PanelHeading("Privacy", "Control machine-specific information in the application chrome")
                ZephyrSettingsRow(
                    title = "Show SDKMAN home path",
                    description = "Display the local SDKMAN path in the toolbar and status bar.",
                ) {
                    ZephyrToggle(
                        checked = settings.showSdkmanHome,
                        onCheckedChange = { visible -> onSettingsChange { it.copy(showSdkmanHome = visible) } },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AboutScreen(state: ZephyrUiState.Ready) {
    val metrics = LocalZephyrMetrics.current
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.spacing * 2),
    ) {
        PageTitle("About Zephyr", "A focused desktop control center for SDKMAN.")
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(metrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CandidateIcon(CandidateKind.Jdk)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Zephyr", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Version 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Kotlin Multiplatform + Compose Desktop for Linux", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelHeading("Runtime", "Connected local environment")
                KeyValueRow("SDKMAN", sdkmanVersionLabel(state))
                KeyValueRow("Installation", state.sdkmanStatus.home ?: "Unavailable")
                KeyValueRow("License", "MIT")
            }
        }
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelHeading("Project", "Open-source and designed for safe local toolchain management")
                LinkText("SDKMAN: https://sdkman.io/")
                Text(
                    "Zephyr delegates package management to SDKMAN, validates command and filesystem boundaries, and keeps destructive cleanup behind explicit review.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PanelHeading(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HealthRow(label: String, healthy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        StatusDot(if (healthy) StatusTone.Success else StatusTone.Warning)
        Text(label)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, healthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(if (healthy) StatusTone.Success else StatusTone.Warning)
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}
