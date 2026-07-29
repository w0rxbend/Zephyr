package com.worxbend.zephyr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.settings.AppSettings
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
    var pendingClean by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var pendingUninstall by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmSelfUpdate by remember { mutableStateOf(false) }

    pendingClean?.let { (candidate, versions) ->
        AlertDialog(
            onDismissRequest = { pendingClean = null },
            title = { Text("Clean Local-Only Versions") },
            text = {
                Text("Zephyr will uninstall ${versions.joinToString()}. If every installed version is cleaned, ${displayNameFor(candidate)} may disappear from Installed.")
            },
            confirmButton = {
                ZephyrDestructiveButton(
                    label = "Clean",
                    onClick = {
                        pendingClean = null
                        viewModel.cleanLocalOnly(candidate, versions)
                    },
                )
            },
            dismissButton = { TextButton(onClick = { pendingClean = null }) { Text("Cancel") } },
        )
    }

    pendingUninstall?.let { (candidate, version) ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("Uninstall $version?") },
            text = {
                Text("SDKMAN will uninstall $version from ${displayNameFor(candidate)}. This cannot be undone without downloading and installing it again.")
            },
            confirmButton = {
                ZephyrDestructiveButton(
                    label = "Uninstall",
                    onClick = {
                        pendingUninstall = null
                        viewModel.uninstall(candidate, version)
                    },
                )
            },
            dismissButton = { TextButton(onClick = { pendingUninstall = null }) { Text("Cancel") } },
        )
    }

    if (confirmSelfUpdate) {
        AlertDialog(
            onDismissRequest = { confirmSelfUpdate = false },
            title = { Text("Check for SDKMAN Updates") },
            text = { Text("SDKMAN may update itself if a new CLI version is available.") },
            confirmButton = {
                Button(onClick = {
                    confirmSelfUpdate = false
                    viewModel.checkSdkmanUpdates()
                }) { Text("Check") }
            },
            dismissButton = { TextButton(onClick = { confirmSelfUpdate = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WorkbenchToolbar(
            state = state,
            darkTheme = darkTheme,
            showSdkmanHome = settings.showSdkmanHome,
            onBack = viewModel::goBack,
            onToggleTheme = onToggleTheme,
            onRefresh = viewModel::refreshInstalled,
            onRefreshMetadata = viewModel::refreshMetadata,
            onScan = viewModel::scanLocalOnly,
            onCheckUpdates = { confirmSelfUpdate = true },
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            WorkbenchSidebar(state = state, onNavigate = viewModel::navigate)
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Content(
                state = state,
                viewModel = viewModel,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onClean = { candidate, versions -> pendingClean = candidate to versions },
                onUninstall = { candidate, version -> pendingUninstall = candidate to version },
            )
        }
        WorkbenchStatusBar(state = state, showSdkmanHome = settings.showSdkmanHome)
    }

    BusyOverlay(state)
    MessageOverlay(state, viewModel::clearMessages)
}

@Composable
private fun WorkbenchToolbar(
    state: ZephyrUiState.Ready,
    darkTheme: Boolean,
    showSdkmanHome: Boolean,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
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
            HeaderThemeButton(darkTheme = darkTheme, onClick = onToggleTheme)
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

        ZephyrSectionLabel("Discover", Modifier.padding(top = 7.dp))
        ZephyrNavigationItem("+J", "Browse JDKs", state.route is ZephyrRoute.BrowseJdks, { onNavigate(ZephyrRoute.BrowseJdks) })
        ZephyrNavigationItem("+S", "Browse SDKs", state.route is ZephyrRoute.BrowseSdks, { onNavigate(ZephyrRoute.BrowseSdks) })

        ZephyrSectionLabel("Maintenance", Modifier.padding(top = 7.dp))
        ZephyrNavigationItem(
            "!",
            "Local-only versions",
            state.route is ZephyrRoute.LocalOnly,
            { onNavigate(ZephyrRoute.LocalOnly) },
            badge = localOnly.takeIf { it > 0 }?.toString(),
        )
        ZephyrNavigationItem("D", "Diagnostics", state.route is ZephyrRoute.Diagnostics, { onNavigate(ZephyrRoute.Diagnostics) })

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
        localOnlyScanInProgress -> "Scanning local-only versions"
        isCatalogLoading -> "Loading SDKMAN catalog"
        detailLoadingCandidate != null -> "Loading package details"
        isRefreshing -> "Refreshing SDKMAN state"
        else -> null
    }
