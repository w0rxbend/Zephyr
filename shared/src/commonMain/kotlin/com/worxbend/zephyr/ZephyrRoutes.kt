package com.worxbend.zephyr

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.JavaVersion
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.toJavaVersion
import com.worxbend.zephyr.settings.AppSettings
import com.worxbend.zephyr.viewmodel.ZephyrRoute
import com.worxbend.zephyr.viewmodel.ZephyrUiState
import com.worxbend.zephyr.viewmodel.ZephyrViewModel

@Composable
internal fun Content(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Box(Modifier.fillMaxSize().padding(metrics.pagePadding)) {
        when (val route = state.route) {
            ZephyrRoute.Overview -> OverviewScreen(state, viewModel, settings)
            ZephyrRoute.InstalledJdk -> InstalledJdkScreen(
                state,
                viewModel::navigate,
                viewModel::setVersionProtected,
                onClean,
            )
            ZephyrRoute.InstalledSdks -> InstalledSdksScreen(state, viewModel::navigate, onClean)
            ZephyrRoute.BrowseJdks -> BrowseScreen(
                state = state,
                viewModel = viewModel,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onClean = onClean,
                onUninstall = onUninstall,
            )
            ZephyrRoute.BrowseSdks -> BrowseScreen(
                title = "Browse SDKs",
                items = state.catalog.filter { it.kind == CandidateKind.Sdk },
                loading = state.isCatalogLoading,
                favoriteCandidates = settings.favoriteCandidates,
                onFavoriteChange = { candidate, favorite ->
                    onSettingsChange {
                        it.copy(
                            favoriteCandidates = it.favoriteCandidates.updated(candidate, favorite),
                        )
                    }
                },
                onOpen = { viewModel.navigate(ZephyrRoute.SdkDetail(it.name)) },
            )
            ZephyrRoute.LocalOnly -> LocalOnlyScreen(state, viewModel::navigate, viewModel::scanLocalOnly, onClean)
            ZephyrRoute.UpdateCenter -> UpdateCenterScreen(state, viewModel)
            ZephyrRoute.BatchUninstall -> BatchUninstallScreen(state, viewModel)
            ZephyrRoute.Diagnostics -> DiagnosticsScreen(
                state,
                viewModel::refreshIntegrity,
                viewModel::exportDiagnostics,
            )
            ZephyrRoute.History -> OperationHistoryScreen(state, viewModel)
            ZephyrRoute.Settings -> SettingsScreen(settings, onSettingsChange)
            ZephyrRoute.About -> AboutScreen(state)
            is ZephyrRoute.JdkDetail -> CandidateDetailScreen(state, route.candidate, true, viewModel, onClean, onUninstall)
            is ZephyrRoute.SdkDetail -> CandidateDetailScreen(state, route.candidate, false, viewModel, onClean, onUninstall)
        }
    }
}

@Composable
private fun InstalledJdkScreen(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
    onProtectionChange: (String, String, Boolean) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val jdk = state.candidates.firstOrNull { it.name == "java" }
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(JavaVersionGrouping.None) }
    val installed = jdk?.installedVersions.orEmpty()
        .asSequence()
        .filter { it.isInstalled }
        .map { it.toJavaVersion() }
        .toList()
    val filtered = installed.filterByQuery(query)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Installed JDK", "${installed.size} local Java version(s) managed by SDKMAN.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, { query = it }, "Search JDKs", Modifier.width(280.dp))
            ZephyrSegmentedControl(
                options = JavaVersionGrouping.entries,
                selected = grouping,
                label = JavaVersionGrouping::label,
                onSelected = { grouping = it },
            )
            Text(
                "${filtered.size} shown",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (installed.isEmpty()) {
            EmptyState("No JDK Installed", "Open Browse JDKs to install a Java version.", "Browse JDKs") {
                onNavigate(ZephyrRoute.BrowseJdks)
            }
            return@Column
        }
        if (filtered.isEmpty()) {
            EmptyState("No matching JDKs", "No installed Java versions match \"$query\".", "Clear search") { query = "" }
            return@Column
        }
        val groups = filtered.groupBy(grouping)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (title, groupVersions) ->
                if (title.isNotBlank()) item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(groupVersions) { version ->
                    val protected = ProtectedVersion("java", version.identifier) in state.protectedVersions
                    JdkVersionCard(
                        version = version,
                        default = jdk?.defaultVersion,
                        isProtected = protected,
                        onToggleProtected = { onProtectionChange("java", version.identifier, !protected) },
                        onClean = { onClean("java", listOf(version.identifier)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledSdksScreen(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val sdks = state.candidates.filter { it.kind == CandidateKind.Sdk }
    var query by remember { mutableStateOf("") }
    val filtered = sdks.filter { candidate ->
        query.isBlank() ||
            candidate.displayName.contains(query, ignoreCase = true) ||
            candidate.name.contains(query, ignoreCase = true) ||
            candidate.defaultVersion.orEmpty().contains(query, ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Installed SDKs", "${sdks.size} package(s) currently present in your SDKMAN candidates directory.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, { query = it }, "Search installed SDKs", Modifier.width(300.dp))
            Text(
                "${filtered.size} shown",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (sdks.isEmpty()) {
            EmptyState("No SDKs Installed", "Open Browse SDKs to install a package.", "Browse SDKs") {
                onNavigate(ZephyrRoute.BrowseSdks)
            }
        } else if (filtered.isEmpty()) {
            EmptyState("No matching SDKs", "No installed packages match \"$query\".", "Clear search") { query = "" }
        } else {
            CandidateGrid(
                candidates = filtered,
                protectedVersions = state.protectedVersions,
                onOpen = { onNavigate(ZephyrRoute.SdkDetail(it.name)) },
                onClean = onClean,
            )
        }
    }
}

@Composable
private fun BrowseScreen(
    title: String,
    items: List<CandidateCatalogItem>,
    loading: Boolean,
    favoriteCandidates: Set<String>,
    onFavoriteChange: (String, Boolean) -> Unit,
    onOpen: (CandidateCatalogItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var catalogFilter by remember { mutableStateOf(CatalogFilter.All) }
    val filtered = items
        .filter { item ->
            val matchesQuery = query.isBlank() ||
                item.displayName.contains(query, ignoreCase = true) ||
                item.name.contains(query, ignoreCase = true) ||
                item.description.orEmpty().contains(query, ignoreCase = true)
            val matchesFilter = when (catalogFilter) {
                CatalogFilter.All -> true
                CatalogFilter.Installed -> item.isInstalled
                CatalogFilter.Available -> !item.isInstalled
            }
            matchesQuery && matchesFilter
        }
        .sortedWith(
            compareByDescending<CandidateCatalogItem> { it.name in favoriteCandidates }
                .thenBy { it.displayName.lowercase() },
        )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle(title, "Explore ${items.size} SDKMAN package(s), then inspect available versions.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchField(query, { query = it }, "Search SDKs", Modifier.width(300.dp))
            ZephyrSegmentedControl(
                options = CatalogFilter.entries,
                selected = catalogFilter,
                label = CatalogFilter::label,
                onSelected = { catalogFilter = it },
            )
            Text(
                "${filtered.size} shown",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (loading) CircularProgressIndicator()
        if (!loading && filtered.isEmpty()) {
            EmptyState(
                if (query.isBlank()) "Catalog Empty" else "No Results",
                if (query.isBlank()) "No packages match the selected filter." else "No packages match \"$query\".",
                "Reset filters",
            ) {
                query = ""
                catalogFilter = CatalogFilter.All
            }
        } else if (!loading) {
            val spacing = LocalZephyrMetrics.current.spacing
            LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                items(filtered, key = { it.name }) { item ->
                    val favorite = item.name in favoriteCandidates
                    PackageCard(
                        item = item,
                        isFavorite = favorite,
                        onToggleFavorite = { onFavoriteChange(item.name, !favorite) },
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    val jdkPackage = state.selectedCandidate ?: state.candidates.firstOrNull { it.name == "java" }
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(JavaVersionGrouping.FeatureVersion) }
    var collapsedGroups by remember(grouping, query) { mutableStateOf(emptySet<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Browse JDKs", "SDKMAN JDK versions. ${jdkPackage?.installedVersions?.size ?: 0} version(s) loaded.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, { query = it }, "Search JDKs", Modifier.width(280.dp))
            ZephyrSegmentedControl(
                options = JavaVersionGrouping.entries,
                selected = grouping,
                label = JavaVersionGrouping::label,
                onSelected = { grouping = it },
            )
            ZephyrToolbarButton(
                label = "Favorite vendors",
                detail = settings.favoriteJdkVendors.size.takeIf { it > 0 }?.toString(),
                onClick = { grouping = JavaVersionGrouping.Provider },
            )
        }

        if (state.detailLoadingCandidate == "java" && jdkPackage == null) {
            CircularProgressIndicator()
            return@Column
        }
        if (jdkPackage == null) {
            EmptyState("JDK Versions Unavailable", "Refresh metadata and try Browse JDKs again.")
            return@Column
        }

        val filteredVersions = jdkPackage.installedVersions
            .map { it.toJavaVersion() }
            .filterByQuery(query)
        if (filteredVersions.isEmpty()) {
            EmptyState("No matching JDKs", "No available Java versions match \"$query\".", "Clear search") { query = "" }
            return@Column
        }
        val groups = filteredVersions.groupBy(grouping)
        val orderedGroups = if (grouping == JavaVersionGrouping.Provider) {
            groups.entries.sortedWith(
                compareByDescending<Map.Entry<String, List<JavaVersion>>> { entry ->
                    entry.value.firstOrNull()?.providerCode in settings.favoriteJdkVendors
                }.thenBy { it.key },
            )
        } else {
            groups.entries.toList()
        }
        val updateTargets = remember(jdkPackage.installedVersions) { jdkPackage.installedVersions.updateTargets() }

        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                orderedGroups.forEach { (title, groupVersions) ->
                    if (title.isNotBlank()) {
                        item {
                            val providerCode = groupVersions.firstOrNull()?.providerCode
                                .takeIf { grouping == JavaVersionGrouping.Provider }
                            val favorite = providerCode in settings.favoriteJdkVendors
                            AccordionHeader(
                                title = title,
                                count = groupVersions.size,
                                collapsed = title in collapsedGroups,
                                onClick = {
                                    collapsedGroups = if (title in collapsedGroups) {
                                        collapsedGroups - title
                                    } else {
                                        collapsedGroups + title
                                    }
                                },
                                actionLabel = providerCode?.let {
                                    if (favorite) "★ Favorite" else "☆ Favorite"
                                },
                                onAction = providerCode?.let { code ->
                                    {
                                        onSettingsChange {
                                            it.copy(
                                                favoriteJdkVendors = it.favoriteJdkVendors.updated(code, !favorite),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (title.isBlank() || title !in collapsedGroups) {
                        items(groupVersions, key = { it.identifier }) { java ->
                            VersionRow(
                                candidateName = "java",
                                version = CandidateVersion(java.identifier, java.isInstalled, java.isDefault, java.isRemoteAvailable),
                                updateTargets = updateTargets,
                                viewModel = viewModel,
                                isProtected = ProtectedVersion("java", java.identifier) in state.protectedVersions,
                                onClean = onClean,
                                onUninstall = onUninstall,
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

private fun Set<String>.updated(value: String, included: Boolean): Set<String> =
    if (included) this + value else this - value

@Composable
private fun LocalOnlyScreen(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
    onScan: () -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val items = state.candidates.filter { it.hasLocalOnlyVersions }
    val versionCount = items.sumOf { it.localOnlyVersionCount }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle(
            "Local-Only Versions",
            "$versionCount installed version(s) across ${items.size} package(s) are no longer listed remotely.",
        )
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalZephyrMetrics.current.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusDot(if (versionCount == 0) StatusTone.Success else StatusTone.Warning)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (versionCount == 0) "Environment is clean" else "Review before removing",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Zephyr re-verifies every selected version against remote metadata before cleanup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ZephyrToolbarButton("Scan again", onClick = onScan)
            }
        }
        if (items.isEmpty()) {
            EmptyState("No Local-Only Versions", "Run Scan whenever SDKMAN metadata changes.", "Run scan", onScan)
        } else {
            CandidateGrid(
                candidates = items,
                protectedVersions = state.protectedVersions,
                onOpen = { candidate ->
                    onNavigate(if (candidate.kind == CandidateKind.Jdk) ZephyrRoute.JdkDetail(candidate.name) else ZephyrRoute.SdkDetail(candidate.name))
                },
                onClean = onClean,
            )
        }
    }
}

@Composable
private fun CandidateDetailScreen(
    state: ZephyrUiState.Ready,
    candidateName: String,
    jdk: Boolean,
    viewModel: ZephyrViewModel,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    val candidate = state.selectedCandidate ?: state.candidates.firstOrNull { it.name == candidateName }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle(candidate?.displayName ?: displayNameFor(candidateName), "Inspect versions and manage the SDKMAN candidate \"$candidateName\".")
        ZephyrPanel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(LocalZephyrMetrics.current.panelPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidate?.description?.let { LinkText(it) }
                candidate?.websiteUrl?.let { LinkText(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("SDKMAN key: $candidateName")
                    candidate?.defaultVersion?.let { Badge("Default: $it", BadgeTone.Primary) }
                    candidate?.installedVersions?.count { it.isInstalled }?.let { Badge("$it installed", BadgeTone.Success) }
                    if (candidate?.hasLocalOnlyVersions == true) {
                        Badge("${candidate.localOnlyVersionCount} local-only", BadgeTone.Warning)
                    }
                }
            }
        }
        if (candidate == null || state.detailLoadingCandidate == candidateName && state.selectedCandidate == null) {
            CircularProgressIndicator()
        } else if (jdk) {
            JdkDetailVersions(candidate, state.protectedVersions, viewModel, onClean, onUninstall)
        } else {
            VersionList(candidate, state.protectedVersions, viewModel, onClean, onUninstall)
        }
    }
}

@Composable
private fun JdkDetailVersions(
    candidate: Candidate,
    protectedVersions: Set<ProtectedVersion>,
    viewModel: ZephyrViewModel,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    var grouping by remember { mutableStateOf(JavaVersionGrouping.FeatureVersion) }
    var query by remember { mutableStateOf("") }
    val groups = candidate.installedVersions
        .map { it.toJavaVersion() }
        .filterByQuery(query)
        .groupBy(grouping)
    val updateTargets = remember(candidate.installedVersions) { candidate.installedVersions.updateTargets() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (candidate.installedVersions.size > 3) {
            SearchField(query, { query = it }, "Search versions", Modifier.width(280.dp))
        }
        ZephyrSegmentedControl(
            options = listOf(JavaVersionGrouping.FeatureVersion, JavaVersionGrouping.Provider),
            selected = grouping,
            label = JavaVersionGrouping::label,
            onSelected = { grouping = it },
        )
    }
    if (groups.isEmpty()) {
        EmptyState(
            if (query.isBlank()) "No versions available" else "No matching versions",
            if (query.isBlank()) "SDKMAN did not return any JDK versions." else "No JDK versions match \"$query\".",
            if (query.isBlank()) null else "Clear search",
            if (query.isBlank()) null else ({ query = "" }),
        )
        return
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { (title, group) ->
                item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(group) { java ->
                    VersionRow(
                        candidateName = candidate.name,
                        version = CandidateVersion(java.identifier, java.isInstalled, java.isDefault, java.isRemoteAvailable),
                        updateTargets = updateTargets,
                        viewModel = viewModel,
                        isProtected = ProtectedVersion(candidate.name, java.identifier) in protectedVersions,
                        onClean = onClean,
                        onUninstall = onUninstall,
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun VersionList(
    candidate: Candidate,
    protectedVersions: Set<ProtectedVersion>,
    viewModel: ZephyrViewModel,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val versions = candidate.installedVersions.filter { version ->
        query.isBlank() || version.version.contains(query, ignoreCase = true) || statusText(version).contains(query, ignoreCase = true)
    }
    val updateTargets = remember(candidate.installedVersions) { candidate.installedVersions.updateTargets() }
    if (candidate.installedVersions.size > 3) {
        SearchField(query, { query = it }, "Search versions", Modifier.width(280.dp))
    }
    if (versions.isEmpty()) {
        EmptyState(
            if (query.isBlank()) "No versions available" else "No matching versions",
            if (query.isBlank()) "SDKMAN did not return any versions for this candidate." else "No versions match \"$query\".",
            if (query.isBlank()) null else "Clear search",
            if (query.isBlank()) null else ({ query = "" }),
        )
        return
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(versions, key = { it.version }) { version ->
                VersionRow(
                    candidateName = candidate.name,
                    version = version,
                    updateTargets = updateTargets,
                    viewModel = viewModel,
                    isProtected = ProtectedVersion(candidate.name, version.version) in protectedVersions,
                    onClean = onClean,
                    onUninstall = onUninstall,
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

private enum class CatalogFilter(val label: String) {
    All("All"),
    Installed("Installed"),
    Available("Available"),
}

@Composable
private fun VersionRow(
    candidateName: String,
    version: CandidateVersion,
    updateTargets: List<CandidateVersion>,
    viewModel: ZephyrViewModel,
    isProtected: Boolean,
    onClean: (String, List<String>) -> Unit,
    onUninstall: (String, String) -> Unit,
) {
    var updateMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(version.version, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (version.isDefault) Badge("Default", BadgeTone.Primary)
                if (version.isInstalled) Badge("Installed", BadgeTone.Neutral)
                if (version.isRemoteAvailable) Badge("Available", BadgeTone.Success) else Badge("Local only", BadgeTone.Warning)
                if (isProtected) Badge("Protected", BadgeTone.Primary)
            }
        }
        if (!version.isInstalled && version.isRemoteAvailable) {
            FilledTonalButton(
                onClick = { viewModel.requestTransaction(SdkmanTransaction.Install(candidateName, version.version)) },
                modifier = Modifier.height(36.dp),
            ) {
                Text("Install")
            }
        }
        if (version.isInstalled && !version.isDefault) {
            OutlinedButton(
                onClick = { viewModel.requestTransaction(SdkmanTransaction.SetDefault(candidateName, version.version)) },
                modifier = Modifier.height(36.dp),
            ) {
                Text("Make default")
            }
            if (version.isRemoteAvailable && !isProtected) {
                OutlinedButton(onClick = { onUninstall(candidateName, version.version) }, modifier = Modifier.height(36.dp)) {
                    Text("Uninstall")
                }
            }
        }
        if (version.isInstalled) {
            OutlinedButton(
                onClick = { viewModel.setVersionProtected(candidateName, version.version, !isProtected) },
                modifier = Modifier.height(36.dp),
            ) {
                Text(if (isProtected) "Unpin" else "Protect")
            }
        }
        if (version.isInstalled && !version.isRemoteAvailable) {
            if (updateTargets.isNotEmpty()) Box {
                OutlinedButton(onClick = { updateMenuOpen = true }, modifier = Modifier.height(36.dp)) { Text("Update") }
                DropdownMenu(expanded = updateMenuOpen, onDismissRequest = { updateMenuOpen = false }) {
                    updateTargets.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.version) },
                            onClick = {
                                updateMenuOpen = false
                                viewModel.requestTransaction(SdkmanTransaction.Install(candidateName, target.version))
                            },
                        )
                    }
                }
            }
        }
        if (version.isInstalled && !version.isRemoteAvailable && !version.isDefault && !isProtected) {
            ZephyrDestructiveButton(
                label = "Clean",
                onClick = { onClean(candidateName, listOf(version.version)) },
                modifier = Modifier.height(36.dp),
            )
        }
    }
}
