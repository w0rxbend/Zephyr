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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.ConnectivityState
import com.worxbend.zephyr.domain.IntegrityCheck
import com.worxbend.zephyr.domain.IntegrityStatus
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
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PanelHeading("Quick actions", "Common SDKMAN workflows")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZephyrToolbarButton("Browse JDKs", onClick = { viewModel.navigate(ZephyrRoute.BrowseJdks) })
                        ZephyrToolbarButton("Browse SDKs", onClick = { viewModel.navigate(ZephyrRoute.BrowseSdks) })
                        ZephyrToolbarButton("Refresh local state", onClick = viewModel::refreshInstalled)
                        ZephyrToolbarButton("Scan local-only", onClick = viewModel::scanLocalOnly)
                    }
                    PanelHeading("Toolchain summary", "Persisted SDKMAN defaults")
                    KeyValueRow("SDKMAN", sdkmanVersionLabel(state))
                    KeyValueRow("Default JDK", jdk?.defaultVersion ?: "Not configured")
                    KeyValueRow("Candidates", state.candidates.size.toString())
                    KeyValueRow("Catalog", if (state.catalog.isEmpty()) "Not loaded" else "${state.catalog.size} packages")
                }
            }
            ZephyrPanel(Modifier.weight(0.75f).fillMaxSize()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
