package com.worxbend.zephyr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.BatchItemStatus
import com.worxbend.zephyr.domain.ConnectivityState
import com.worxbend.zephyr.domain.DiskImpactEstimate
import com.worxbend.zephyr.domain.DiskImpactKind
import com.worxbend.zephyr.domain.PlannedSdkmanCommand
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.formatByteSize
import com.worxbend.zephyr.domain.requiresNetwork
import com.worxbend.zephyr.settings.AppSettings
import com.worxbend.zephyr.settings.recordRecentCandidate
import com.worxbend.zephyr.viewmodel.ZephyrRoute
import com.worxbend.zephyr.viewmodel.ZephyrUiState
import com.worxbend.zephyr.viewmodel.ZephyrViewModel
import org.jetbrains.compose.resources.painterResource
import zephyr.shared.generated.resources.Res
import zephyr.shared.generated.resources.ic_arrow_left
import zephyr.shared.generated.resources.ic_moon
import zephyr.shared.generated.resources.ic_sun

@Composable
internal fun ZephyrScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    var globalSearchOpen by remember { mutableStateOf(false) }
    var commandPaletteOpen by remember { mutableStateOf(false) }
    val activateSearchTarget: (GlobalSearchTarget) -> Unit = { target ->
        globalSearchOpen = false
        commandPaletteOpen = false
        when (target) {
            is GlobalSearchTarget.Navigate -> viewModel.navigate(target.route)
            is GlobalSearchTarget.Execute -> when (target.action) {
                GlobalSearchAction.RefreshInstalled -> viewModel.refreshInstalled()
                GlobalSearchAction.ScanLocalOnly -> viewModel.scanLocalOnly()
                GlobalSearchAction.RefreshConnectivity -> viewModel.refreshConnectivity()
                GlobalSearchAction.RefreshMetadata -> {
                    viewModel.requestTransaction(SdkmanTransaction.RefreshMetadata)
                }
                GlobalSearchAction.CheckUpdates -> {
                    viewModel.requestTransaction(SdkmanTransaction.SelfUpdate)
                }
            }
        }
    }

    LaunchedEffect(state.route) {
        val candidate = when (val route = state.route) {
            is ZephyrRoute.JdkDetail -> route.candidate
            is ZephyrRoute.SdkDetail -> route.candidate
            else -> null
        }
        if (candidate != null) {
            onSettingsChange { it.recordRecentCandidate(candidate) }
        }
    }

    state.pendingTransaction?.let { transaction ->
        TransactionPreviewDialog(
            transaction = transaction,
            diskImpact = state.pendingTransactionDiskImpact,
            onConfirm = viewModel::confirmTransaction,
            onDismiss = viewModel::dismissTransaction,
        )
    }

    if (globalSearchOpen) {
        GlobalSearchDialog(
            state = state,
            onDismiss = { globalSearchOpen = false },
            onSelect = activateSearchTarget,
        )
    }
    if (commandPaletteOpen) {
        GlobalSearchDialog(
            state = state,
            mode = SearchOverlayMode.CommandPalette,
            onDismiss = { commandPaletteOpen = false },
            onSelect = activateSearchTarget,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || (!event.isCtrlPressed && !event.isMetaPressed)) {
                    false
                } else {
                    when {
                        event.key == Key.K && !event.isShiftPressed -> {
                            commandPaletteOpen = false
                            globalSearchOpen = true
                            true
                        }
                        event.key == Key.P && event.isShiftPressed -> {
                            globalSearchOpen = false
                            commandPaletteOpen = true
                            true
                        }
                        event.key == Key.R && event.isShiftPressed -> {
                            activateSearchTarget(
                                GlobalSearchTarget.Execute(GlobalSearchAction.RefreshInstalled),
                            )
                            true
                        }
                        event.key == Key.L && event.isShiftPressed -> {
                            activateSearchTarget(
                                GlobalSearchTarget.Execute(GlobalSearchAction.ScanLocalOnly),
                            )
                            true
                        }
                        event.key == Key.D && event.isShiftPressed -> {
                            activateSearchTarget(GlobalSearchTarget.Navigate(ZephyrRoute.Diagnostics))
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        WorkbenchToolbar(
            state = state,
            darkTheme = darkTheme,
            showSdkmanHome = settings.showSdkmanHome,
            onBack = viewModel::goBack,
            onOpenSearch = { globalSearchOpen = true },
            onToggleTheme = onToggleTheme,
            onRefresh = viewModel::refreshInstalled,
            onRefreshConnectivity = viewModel::refreshConnectivity,
            onRefreshMetadata = { viewModel.requestTransaction(SdkmanTransaction.RefreshMetadata) },
            onScan = viewModel::scanLocalOnly,
            onCheckUpdates = { viewModel.requestTransaction(SdkmanTransaction.SelfUpdate) },
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            WorkbenchSidebar(state = state, onNavigate = viewModel::navigate)
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Content(
                state = state,
                viewModel = viewModel,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onClean = { candidate, versions ->
                    viewModel.requestTransaction(SdkmanTransaction.CleanLocalOnly(candidate, versions))
                },
                onUninstall = { candidate, version ->
                    viewModel.requestTransaction(SdkmanTransaction.Uninstall(candidate, version))
                },
            )
        }
        WorkbenchStatusBar(state = state, showSdkmanHome = settings.showSdkmanHome)
    }

    BusyOverlay(state)
    MessageOverlay(
        state = state,
        onDismiss = viewModel::clearMessages,
        onOpenRecovery = { viewModel.navigate(ZephyrRoute.History) },
    )
}

@Composable
internal fun TransactionPreviewDialog(
    transaction: SdkmanTransaction,
    diskImpact: DiskImpactEstimate? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(transaction.title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(transaction.description)
                Badge(
                    if (transaction.requiresNetwork) "Network required" else "Works offline",
                    if (transaction.requiresNetwork) BadgeTone.Warning else BadgeTone.Success,
                )
                diskImpact?.let { DiskImpactSummary(it) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Typed command plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ZephyrPanel(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        Column(
                            modifier = Modifier
                                .padding(LocalZephyrMetrics.current.panelPadding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            transaction.commands.forEachIndexed { index, command ->
                                PlannedCommandRow(index + 1, command)
                            }
                        }
                    }
                    Text(
                        "Arguments are validated and passed through Zephyr's typed SDKMAN boundary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (transaction.destructive) {
                ZephyrDestructiveButton(transaction.confirmationLabel, onConfirm)
            } else {
                Button(onClick = onConfirm) { Text(transaction.confirmationLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiskImpactSummary(estimate: DiskImpactEstimate) {
    val label = when (estimate.kind) {
        DiskImpactKind.Required -> "Required disk space"
        DiskImpactKind.Reclaimable -> "Reclaimable disk space"
        DiskImpactKind.None -> "Disk impact"
        DiskImpactKind.Unknown -> "Disk impact unavailable"
    }
    val value = when (estimate.kind) {
        DiskImpactKind.None -> "No material change"
        else -> estimate.bytes?.let(::formatByteSize) ?: "Not measurable"
    }
    ZephyrPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LocalZephyrMetrics.current.panelPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    estimate.confidence.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estimate.availableBytes?.let {
                    Text(
                        "${formatByteSize(it)} currently available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                estimate.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlannedCommandRow(
    step: Int,
    command: PlannedSdkmanCommand,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Badge(step.toString())
        Text(command.action.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        command.candidate?.let { Badge(it, BadgeTone.Primary) }
        command.version?.let { Badge(it) }
    }
}

@Composable
private fun WorkbenchToolbar(
    state: ZephyrUiState.Ready,
    darkTheme: Boolean,
    showSdkmanHome: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshConnectivity: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onScan: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    val busy = state.busyLabel() != null
    Surface(
        modifier = Modifier.fillMaxWidth().height(metrics.toolbarHeight),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (state.previousRoute != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(metrics.controlHeight)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_left),
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Z", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.width(190.dp)) {
                Text(
                    headerTitle(state),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (showSdkmanHome) state.sdkmanStatus.home.orEmpty() else sdkmanVersionLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            GlobalSearchButton(onClick = onOpenSearch)
            HeaderThemeButton(darkTheme = darkTheme, onClick = onToggleTheme)
            ZephyrToolbarButton(
                label = "Network",
                detail = state.connectivityStatus.state.label.lowercase(),
                onClick = onRefreshConnectivity,
                enabled = state.connectivityStatus.state != ConnectivityState.Checking,
            )
            ZephyrToolbarButton("Refresh", onClick = onRefresh, enabled = !busy)
            ZephyrToolbarButton(
                label = "Metadata",
                detail = metadataShortLabel(state.sdkmanStatus.metadataStatus),
                onClick = onRefreshMetadata,
                enabled = !busy,
            )
            ZephyrToolbarButton("Scan", onClick = onScan, enabled = !busy)
            ZephyrToolbarButton(
                label = "SDKMAN update",
                detail = selfUpdateShortLabel(state.sdkmanStatus.selfUpdateStatus),
                onClick = onCheckUpdates,
                enabled = !busy,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun GlobalSearchButton(onClick: () -> Unit) {
    val metrics = LocalZephyrMetrics.current
    Surface(
        onClick = onClick,
        modifier = Modifier.width(190.dp).height(metrics.controlHeight),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(metrics.cornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("⌕", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Search / commands",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Badge("Ctrl/⌘ K")
        }
    }
}

@Composable
private fun WorkbenchSidebar(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    val installedSdks = state.candidates.count { it.kind == CandidateKind.Sdk }
    val localOnly = state.candidates.sumOf { it.localOnlyVersionCount }
    Column(
        modifier = Modifier
            .width(metrics.navigationWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ZephyrSectionLabel("Workspace")
        ZephyrNavigationItem("O", "Overview", state.route is ZephyrRoute.Overview, { onNavigate(ZephyrRoute.Overview) })
        ZephyrNavigationItem(
            "J",
            "Installed JDK",
            state.route is ZephyrRoute.InstalledJdk,
            { onNavigate(ZephyrRoute.InstalledJdk) },
            badge = state.candidates.firstOrNull { it.kind == CandidateKind.Jdk }?.installedVersions?.count { it.isInstalled }?.toString(),
        )
        ZephyrNavigationItem(
            "S",
            "Installed SDKs",
            state.route is ZephyrRoute.InstalledSdks,
            { onNavigate(ZephyrRoute.InstalledSdks) },
            badge = installedSdks.toString(),
        )
        ZephyrNavigationItem(
            "P",
            "Toolchain Profiles",
            state.route is ZephyrRoute.Profiles,
            { onNavigate(ZephyrRoute.Profiles) },
        )
        ZephyrNavigationItem(
            "↥",
            "Import .sdkmanrc",
            state.route is ZephyrRoute.ProjectImport,
            { onNavigate(ZephyrRoute.ProjectImport) },
        )
        ZephyrNavigationItem(
            "↧",
            "Export .sdkmanrc",
            state.route is ZephyrRoute.ProjectExport,
            { onNavigate(ZephyrRoute.ProjectExport) },
        )

        ZephyrSectionLabel("Discover", Modifier.padding(top = 7.dp))
        ZephyrNavigationItem("+J", "Browse JDKs", state.route is ZephyrRoute.BrowseJdks, { onNavigate(ZephyrRoute.BrowseJdks) })
        ZephyrNavigationItem("+S", "Browse SDKs", state.route is ZephyrRoute.BrowseSdks, { onNavigate(ZephyrRoute.BrowseSdks) })
        ZephyrNavigationItem(
            "≡",
            "Compare versions",
            state.route is ZephyrRoute.Comparison,
            { onNavigate(ZephyrRoute.Comparison) },
        )

        ZephyrSectionLabel("Maintenance", Modifier.padding(top = 7.dp))
        ZephyrNavigationItem(
            "!",
            "Local-only versions",
            state.route is ZephyrRoute.LocalOnly,
            { onNavigate(ZephyrRoute.LocalOnly) },
            badge = localOnly.takeIf { it > 0 }?.toString(),
        )
        val updates = availableCandidateUpdates(state.candidates, state.catalog).size
        ZephyrNavigationItem(
            "↑",
            "Update Center",
            state.route is ZephyrRoute.UpdateCenter,
            { onNavigate(ZephyrRoute.UpdateCenter) },
            badge = updates.takeIf { it > 0 }?.toString(),
        )
        ZephyrNavigationItem(
            "−",
            "Batch Uninstall",
            state.route is ZephyrRoute.BatchUninstall,
            { onNavigate(ZephyrRoute.BatchUninstall) },
        )
        ZephyrNavigationItem("D", "Diagnostics", state.route is ZephyrRoute.Diagnostics, { onNavigate(ZephyrRoute.Diagnostics) })
        ZephyrNavigationItem(
            "H",
            "Operation history",
            state.route is ZephyrRoute.History,
            { onNavigate(ZephyrRoute.History) },
            badge = state.operationJournal.size.takeIf { it > 0 }?.toString(),
        )

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        ZephyrNavigationItem("⚙", "Settings", state.route is ZephyrRoute.Settings, { onNavigate(ZephyrRoute.Settings) })
        ZephyrNavigationItem("i", "About", state.route is ZephyrRoute.About, { onNavigate(ZephyrRoute.About) })
    }
}

@Composable
private fun WorkbenchStatusBar(
    state: ZephyrUiState.Ready,
    showSdkmanHome: Boolean,
) {
    val metrics = LocalZephyrMetrics.current
    val busyLabel = state.busyLabel()
    Surface(
        modifier = Modifier.fillMaxWidth().height(metrics.statusBarHeight),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(if (busyLabel == null) StatusTone.Success else StatusTone.Accent)
            Text(busyLabel ?: "Ready", style = MaterialTheme.typography.labelSmall)
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusDot(
                when (state.connectivityStatus.state) {
                    ConnectivityState.Online -> StatusTone.Success
                    ConnectivityState.Offline -> StatusTone.Error
                    ConnectivityState.Checking -> StatusTone.Accent
                    ConnectivityState.Unknown -> StatusTone.Neutral
                },
            )
            Text(
                state.connectivityStatus.state.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${state.candidates.size} candidates",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                jdkSubtitle(state),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (showSdkmanHome) {
                Text(
                    state.sdkmanStatus.home.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                sdkmanVersionLabel(state),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HeaderThemeButton(
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(metrics.controlHeight),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(metrics.cornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (darkTheme) Res.drawable.ic_sun else Res.drawable.ic_moon),
                contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun ZephyrUiState.Ready.busyLabel(): String? =
    when {
        batchUninstallProgress.any { it.status == BatchItemStatus.Running } -> {
            val completed = batchUninstallProgress.count {
                it.status == BatchItemStatus.Succeeded || it.status == BatchItemStatus.Failed
            }
            "Uninstalling ${completed + 1} of ${batchUninstallProgress.size}"
        }
        batchInstallProgress.any { it.status == BatchItemStatus.Running } -> {
            val completed = batchInstallProgress.count {
                it.status == BatchItemStatus.Succeeded || it.status == BatchItemStatus.Failed
            }
            "Installing ${completed + 1} of ${batchInstallProgress.size}"
        }
        localOnlyScanInProgress -> "Scanning local-only versions"
        isCatalogLoading -> "Loading SDKMAN catalog"
        detailLoadingCandidate != null -> "Loading package details"
        journalExportInProgress -> "Exporting operation journal"
        diagnosticsExportInProgress -> "Exporting support bundle"
        transactionPreviewLoading -> "Calculating disk impact"
        isRefreshing -> "Refreshing SDKMAN state"
        else -> null
    }
