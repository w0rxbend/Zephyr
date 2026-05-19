package com.worxbend.zephyr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.compose.resources.painterResource
import zephyr.shared.generated.resources.Res
import zephyr.shared.generated.resources.ic_arrow_left
import zephyr.shared.generated.resources.ic_moon
import zephyr.shared.generated.resources.ic_sun
import com.worxbend.zephyr.data.createSdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.JavaVersion
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.javaProviderName
import com.worxbend.zephyr.domain.toJavaVersion
import com.worxbend.zephyr.viewmodel.ZephyrRoute
import com.worxbend.zephyr.viewmodel.ZephyrUiState
import com.worxbend.zephyr.viewmodel.ZephyrViewModel

private val ZephyrLightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF163B7A),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFF3),
    onSecondaryContainer = Color(0xFF1F2937),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF5F636B),
    outline = Color(0xFFD0D5DD),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFDC2626),
    surfaceContainerLow = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF18181B),
    inverseOnSurface = Color(0xFFF8FAFC),
)

private val ZephyrDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2F63),
    primaryContainer = Color(0xFF1F3A5F),
    onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = Color(0xFFB8C0CC),
    onSecondary = Color(0xFF1A1D22),
    secondaryContainer = Color(0xFF282D35),
    onSecondaryContainer = Color(0xFFE6E9EF),
    background = Color(0xFF101114),
    onBackground = Color(0xFFEDEFF3),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFEDEFF3),
    surfaceVariant = Color(0xFF202329),
    onSurfaceVariant = Color(0xFFB7BDC7),
    outline = Color(0xFF3A3F48),
    outlineVariant = Color(0xFF2A2E36),
    error = Color(0xFFF87171),
    surfaceContainerLow = Color(0xFF1B1E23),
    inverseSurface = Color(0xFFEDEFF3),
    inverseOnSurface = Color(0xFF17191D),
)

@Composable
@Preview
fun App() {
    val viewModel = remember { ZephyrViewModel(createSdkmanRepository()) }
    val state by viewModel.state.collectAsState()
    var darkTheme by remember { mutableStateOf(isSystemDarkMode()) }

    MaterialTheme(colorScheme = if (darkTheme) ZephyrDarkColors else ZephyrLightColors) {
        Surface(Modifier.fillMaxSize()) {
            when (val current = state) {
                ZephyrUiState.Loading -> LoadingScreen()
                is ZephyrUiState.SdkmanMissing -> SdkmanMissingScreen(current.message, viewModel::refreshAll)
                is ZephyrUiState.Ready -> ZephyrScreen(
                    state = current,
                    viewModel = viewModel,
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().safeContentPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("Loading SDKMAN state")
        }
    }
}

@Composable
private fun SdkmanMissingScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeContentPadding().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SDKMAN Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Zephyr is a GUI wrapper around SDKMAN and cannot manage JDKs or SDKs until SDKMAN is installed.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        CodeBlock("""curl -s "https://get.sdkman.io" | bash""")
        Spacer(Modifier.height(12.dp))
        Text("Restart your terminal or desktop session after installation, then retry.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZephyrScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    var pendingClean by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var confirmSelfUpdate by remember { mutableStateOf(false) }

    if (pendingClean != null) {
        val (candidate, versions) = pendingClean!!
        AlertDialog(
            onDismissRequest = { pendingClean = null },
            title = { Text("Clean Local-Only Versions") },
            text = {
                Text("Zephyr will uninstall ${versions.joinToString()}. If every installed version is cleaned, ${displayNameFor(candidate)} may disappear from Installed.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingClean = null
                    viewModel.cleanLocalOnly(candidate, versions)
                }) { Text("Clean") }
            },
            dismissButton = { TextButton(onClick = { pendingClean = null }) { Text("Cancel") } },
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 2.dp,
            ) {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                headerTitle(state),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    jdkSubtitle(state),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                )
                                Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(999.dp)))
                                Text(
                                    sdkmanVersionLabel(state),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                )
                                Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(999.dp)))
                                Text(
                                    state.sdkmanStatus.home.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (state.previousRoute != null) {
                            IconButton(onClick = viewModel::goBack) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_arrow_left),
                                    contentDescription = "Back",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    actions = {
                        HeaderIconButton(
                            onClick = onToggleTheme,
                        ) {
                            Icon(
                                painter = painterResource(if (darkTheme) Res.drawable.ic_sun else Res.drawable.ic_moon),
                                contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TopBarSeparator()
                        HeaderActionButton("Refresh", onClick = viewModel::refreshInstalled)
                        TopBarSeparator()
                        HeaderActionButton(
                            label = "Metadata",
                            detail = metadataShortLabel(state.sdkmanStatus.metadataStatus),
                            onClick = viewModel::refreshMetadata,
                        )
                        HeaderActionButton("Scan", onClick = viewModel::scanLocalOnly)
                        TopBarSeparator()
                        HeaderActionButton(
                            label = "Check Updates",
                            detail = selfUpdateShortLabel(state.sdkmanStatus.selfUpdateStatus),
                            onClick = { confirmSelfUpdate = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Sidebar(
                state = state,
                onNavigate = viewModel::navigate,
            )
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Content(
                state = state,
                viewModel = viewModel,
                onClean = { candidate, versions -> pendingClean = candidate to versions },
            )
        }
    }

    BusyOverlay(state)
    MessageOverlay(state, viewModel::clearMessages)
}

@Composable
private fun Sidebar(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
) {
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavRow("Installed JDK", state.route is ZephyrRoute.InstalledJdk) { onNavigate(ZephyrRoute.InstalledJdk) }
        NavRow("Installed SDKs", state.route is ZephyrRoute.InstalledSdks) { onNavigate(ZephyrRoute.InstalledSdks) }
        NavRow("Browse JDKs", state.route is ZephyrRoute.BrowseJdks) { onNavigate(ZephyrRoute.BrowseJdks) }
        NavRow("Browse SDKs", state.route is ZephyrRoute.BrowseSdks) { onNavigate(ZephyrRoute.BrowseSdks) }
        if (state.localOnlyScanInProgress || state.candidates.any { it.hasLocalOnlyVersions }) {
            NavRow("Local-Only Versions", state.route is ZephyrRoute.LocalOnly) { onNavigate(ZephyrRoute.LocalOnly) }
        }
    }
}

@Composable
private fun HeaderActionButton(label: String, detail: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = if (detail == null) 8.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun NavRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(background).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        color = color,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun TopBarSeparator() {
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "⌕",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun Content(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
    onClean: (String, List<String>) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
        when (val route = state.route) {
            ZephyrRoute.InstalledJdk -> InstalledJdkScreen(state, viewModel::navigate, onClean)
            ZephyrRoute.InstalledSdks -> InstalledSdksScreen(state, viewModel::navigate, onClean)
            ZephyrRoute.BrowseJdks -> BrowseScreen(
                state = state,
                viewModel = viewModel,
            )
            ZephyrRoute.BrowseSdks -> BrowseScreen(
                title = "Browse SDKs",
                items = state.catalog.filter { it.kind == CandidateKind.Sdk },
                loading = state.isCatalogLoading,
                onOpen = { viewModel.navigate(ZephyrRoute.SdkDetail(it.name)) },
            )
            ZephyrRoute.LocalOnly -> LocalOnlyScreen(state, viewModel::navigate, onClean)
            is ZephyrRoute.JdkDetail -> CandidateDetailScreen(state, route.candidate, true, viewModel, onClean)
            is ZephyrRoute.SdkDetail -> CandidateDetailScreen(state, route.candidate, false, viewModel, onClean)
        }
    }
}

@Composable
private fun InstalledJdkScreen(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val jdk = state.candidates.firstOrNull { it.name == "java" }
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf("None") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Installed JDK", "Local Java versions managed by SDKMAN.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "Search JDKs", Modifier.width(280.dp))
            listOf("None", "By Version", "By Provider").forEach { mode ->
                AssistChip(onClick = { grouping = mode }, label = { Text(mode) })
            }
        }
        if (jdk == null || jdk.installedVersions.none { it.isInstalled }) {
            EmptyState("No JDK Installed", "Open Browse JDKs to install a Java version.", "Browse JDKs") {
                onNavigate(ZephyrRoute.BrowseJdks)
            }
            return@Column
        }
        val versions = jdk.installedVersions.filter { it.isInstalled }
            .map { it.toJavaVersion() }
            .filter { java ->
                query.isBlank() ||
                    java.identifier.contains(query, ignoreCase = true) ||
                    java.featureVersion.contains(query, ignoreCase = true) ||
                    java.providerName.orEmpty().contains(query, ignoreCase = true)
            }
        val groups = when (grouping) {
            "By Version" -> versions.groupBy { "JDK ${it.featureVersion}" }
            "By Provider" -> versions.groupBy { it.providerName ?: "Other Providers" }
            else -> mapOf("" to versions)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (title, groupVersions) ->
                if (title.isNotBlank()) item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(groupVersions) { version ->
                    JdkVersionCard(version, jdk.currentVersion, state.jdkSelection.defaultIdentifier) {
                        onClean("java", listOf(version.identifier))
                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Installed SDKs", "Packages currently present in your SDKMAN candidates directory.")
        if (sdks.isEmpty()) {
            EmptyState("No SDKs Installed", "Open Browse SDKs to install a package.", "Browse SDKs") {
                onNavigate(ZephyrRoute.BrowseSdks)
            }
        } else {
            CandidateGrid(sdks, onOpen = { onNavigate(ZephyrRoute.SdkDetail(it.name)) }, onClean = onClean)
        }
    }
}

@Composable
private fun BrowseScreen(
    title: String,
    items: List<CandidateCatalogItem>,
    loading: Boolean,
    onOpen: (CandidateCatalogItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = items.filter { item ->
        query.isBlank() ||
            item.displayName.contains(query, ignoreCase = true) ||
            item.name.contains(query, ignoreCase = true) ||
            item.description.orEmpty().contains(query, ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PageTitle(title, "SDKMAN package catalog. ${items.size} package(s) loaded.")
            SearchField(query, { query = it }, "Search SDKs", Modifier.width(280.dp))
        }
        if (loading) CircularProgressIndicator()
        if (!loading && filtered.isEmpty()) {
            EmptyState(
                if (query.isBlank()) "Catalog Empty" else "No Results",
                if (query.isBlank()) "Refresh candidate metadata and try again." else "No packages match \"$query\".",
            )
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(230.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.name }) { item ->
                    PackageCard(item, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun BrowseScreen(
    state: ZephyrUiState.Ready,
    viewModel: ZephyrViewModel,
) {
    val jdkPackage = state.selectedCandidate ?: state.candidates.firstOrNull { it.name == "java" }
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf("By Version") }
    var collapsedGroups by remember(grouping, query) { mutableStateOf(emptySet<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Browse JDKs", "SDKMAN JDK versions. ${jdkPackage?.installedVersions?.size ?: 0} version(s) loaded.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "Search JDKs", Modifier.width(280.dp))
            listOf("None", "By Version", "By Provider").forEach { mode ->
                AssistChip(onClick = { grouping = mode }, label = { Text(mode) })
            }
        }

        if (state.isRefreshing && jdkPackage == null) {
            CircularProgressIndicator()
            return@Column
        }
        if (jdkPackage == null) {
            EmptyState("JDK Versions Unavailable", "Refresh metadata and try Browse JDKs again.")
            return@Column
        }

        val versions = jdkPackage.installedVersions
            .map { it.toJavaVersion() }
            .filter { java ->
                query.isBlank() ||
                    java.identifier.contains(query, ignoreCase = true) ||
                    java.featureVersion.contains(query, ignoreCase = true) ||
                    java.providerName.orEmpty().contains(query, ignoreCase = true)
            }
        val groups = when (grouping) {
            "By Provider" -> versions.groupBy { it.providerName ?: "Other Providers" }
            "By Version" -> versions.groupBy { "JDK ${it.featureVersion}" }
            else -> mapOf("" to versions)
        }

        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { (title, groupVersions) ->
                    if (title.isNotBlank()) {
                        item {
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
                            )
                        }
                    }
                    if (title.isBlank() || title !in collapsedGroups) {
                        items(groupVersions, key = { it.identifier }) { java ->
                            VersionRow(
                                candidateName = "java",
                                version = CandidateVersion(java.identifier, java.isInstalled, java.isCurrent, java.isRemoteAvailable),
                                updateTargets = jdkPackage.installedVersions.updateTargets(),
                                viewModel = viewModel,
                                onClean = { candidate, versions -> viewModel.cleanLocalOnly(candidate, versions) },
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

@Composable
private fun LocalOnlyScreen(
    state: ZephyrUiState.Ready,
    onNavigate: (ZephyrRoute) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    val items = state.candidates.filter { it.hasLocalOnlyVersions }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle("Local-Only Versions", "Installed versions that are no longer listed remotely by SDKMAN.")
        if (items.isEmpty()) {
            EmptyState("No Local-Only Versions", "Run Scan to audit all installed packages.")
        } else {
            CandidateGrid(
                items,
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
) {
    val candidate = state.selectedCandidate ?: state.candidates.firstOrNull { it.name == candidateName }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        PageTitle(candidate?.displayName ?: displayNameFor(candidateName), "SDKMAN key: $candidateName")
        candidate?.description?.let { LinkText(it) }
        candidate?.websiteUrl?.let { LinkText(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            candidate?.currentVersion?.let { Badge("Current: $it") }
            candidate?.installedVersions?.count { it.isInstalled }?.let { Badge("$it installed") }
            if (candidate?.hasLocalOnlyVersions == true) Badge("${candidate.localOnlyVersionCount} local-only")
        }
        if (candidate == null || state.isRefreshing && state.selectedCandidate == null) {
            CircularProgressIndicator()
        } else if (jdk) {
            JdkDetailVersions(candidate, viewModel, onClean)
        } else {
            VersionList(candidate, viewModel, onClean)
        }
    }
}

@Composable
private fun JdkDetailVersions(candidate: Candidate, viewModel: ZephyrViewModel, onClean: (String, List<String>) -> Unit) {
    var grouping by remember { mutableStateOf("By Version") }
    var query by remember { mutableStateOf("") }
    val versions = candidate.installedVersions.map { it.toJavaVersion() }
        .filter { java ->
            query.isBlank() ||
                java.identifier.contains(query, ignoreCase = true) ||
                java.featureVersion.contains(query, ignoreCase = true) ||
                java.providerName.orEmpty().contains(query, ignoreCase = true)
        }
    val groups = if (grouping == "By Provider") {
        versions.groupBy { it.providerName ?: "Other Providers" }
    } else {
        versions.groupBy { "JDK ${it.featureVersion}" }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (candidate.installedVersions.size > 3) {
            SearchField(query, { query = it }, "Search versions", Modifier.width(280.dp))
        }
        AssistChip(onClick = { grouping = "By Version" }, label = { Text("By Version") })
        AssistChip(onClick = { grouping = "By Provider" }, label = { Text("By Provider") })
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
                        version = CandidateVersion(java.identifier, java.isInstalled, java.isCurrent, java.isRemoteAvailable),
                        updateTargets = candidate.installedVersions.updateTargets(),
                        viewModel = viewModel,
                        onClean = onClean,
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
private fun VersionList(candidate: Candidate, viewModel: ZephyrViewModel, onClean: (String, List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val versions = candidate.installedVersions.filter { version ->
        query.isBlank() || version.version.contains(query, ignoreCase = true) || statusText(version).contains(query, ignoreCase = true)
    }
    if (candidate.installedVersions.size > 3) {
        SearchField(query, { query = it }, "Search versions", Modifier.width(280.dp))
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(versions, key = { it.version }) { version ->
                VersionRow(candidate.name, version, candidate.installedVersions.updateTargets(), viewModel, onClean)
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun VersionRow(
    candidateName: String,
    version: CandidateVersion,
    updateTargets: List<CandidateVersion>,
    viewModel: ZephyrViewModel,
    onClean: (String, List<String>) -> Unit,
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
                if (version.isCurrent) Badge("Current", BadgeTone.Primary)
                if (version.isInstalled) Badge("Installed", BadgeTone.Neutral)
                if (version.isRemoteAvailable) Badge("Available", BadgeTone.Success) else Badge("Local only", BadgeTone.Warning)
            }
        }
        if (!version.isInstalled && version.isRemoteAvailable) {
            FilledTonalButton(onClick = { viewModel.install(candidateName, version.version) }, modifier = Modifier.height(36.dp)) { Text("Install") }
        }
        if (version.isInstalled && !version.isCurrent) {
            OutlinedButton(onClick = { viewModel.use(candidateName, version.version) }, modifier = Modifier.height(36.dp)) { Text("Use") }
            OutlinedButton(onClick = { viewModel.setDefault(candidateName, version.version) }, modifier = Modifier.height(36.dp)) { Text("Default") }
            OutlinedButton(onClick = { viewModel.uninstall(candidateName, version.version) }, modifier = Modifier.height(36.dp)) { Text("Uninstall") }
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
                                viewModel.install(candidateName, target.version)
                            },
                        )
                    }
                }
            }
        }
        if (version.isInstalled && !version.isRemoteAvailable && !version.isCurrent) {
            Button(onClick = { onClean(candidateName, listOf(version.version)) }, modifier = Modifier.height(36.dp)) { Text("Clean") }
        }
    }
}

@Composable
private fun CandidateGrid(
    candidates: List<Candidate>,
    onOpen: (Candidate) -> Unit,
    onClean: (String, List<String>) -> Unit,
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(230.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(candidates, key = { it.name }) { candidate ->
            CandidateCard(candidate, onClick = { onOpen(candidate) }, onClean = {
                onClean(candidate.name, candidate.localOnlyVersions)
            })
        }
    }
}

@Composable
private fun CandidateCard(candidate: Candidate, onClick: () -> Unit, onClean: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().height(214.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MockIcon(candidate.kind)
                Column(Modifier.weight(1f)) {
                    Text(candidate.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SDKMAN key: ${candidate.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            LinkText(
                text = candidate.description ?: "${candidate.installedVersions.count { it.isInstalled }} installed version(s)",
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
            )
            Spacer(Modifier.weight(1f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidate.currentVersion?.let { Badge("Current: $it") }
                if (candidate.hasLocalOnlyVersions) Badge("${candidate.localOnlyVersionCount} local-only")
            }
            if (candidate.hasLocalOnlyVersions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onClean,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("Clean", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageCard(item: CandidateCatalogItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().height(190.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MockIcon(item.kind)
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SDKMAN key: ${item.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            LinkText(
                text = item.description ?: "Available from SDKMAN.",
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
            )
            Spacer(Modifier.weight(1f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.stableVersion?.let { Badge("Stable: $it") }
                if (item.isInstalled) Badge("Installed")
            }
        }
    }
}

@Composable
private fun JdkVersionCard(version: JavaVersion, current: String?, default: String?, onClean: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MockIcon(CandidateKind.Jdk)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("JDK ${version.featureVersion}", fontWeight = FontWeight.SemiBold)
                Text(version.identifier)
                Text(version.providerName ?: javaProviderName(version.providerCode) ?: "Provider unknown", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("SDKMAN key: java")
                    if (version.identifier == current) Badge("Current")
                    if (version.identifier == default) Badge("Default")
                    if (!version.isRemoteAvailable) Badge("Local only")
                }
            }
            if (!version.isRemoteAvailable && version.identifier != current) {
                OutlinedButton(onClick = onClean) { Text("Clean") }
            }
            if (!version.isRemoteAvailable && version.identifier == current) {
                Text("Switch JDK first", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { text.withClickableLinks(linkColor) }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "url", start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}

private fun String.withClickableLinks(linkColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    val urlPattern = Regex("""https?://[^\s)]+""")
    return buildAnnotatedString {
        append(this@withClickableLinks)
        urlPattern.findAll(this@withClickableLinks).forEach { match ->
            addStringAnnotation("url", match.value.trimEnd('.', ','), match.range.first, match.range.last + 1)
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                match.range.first,
                match.range.last + 1,
            )
        }
    }
}

@Composable
private fun AccordionHeader(title: String, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (collapsed) ">" else "v", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Badge("$count")
    }
}

@Composable
private fun EmptyState(title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MockIcon(kind: CandidateKind) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (kind == CandidateKind.Jdk) "JDK" else "SDK",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private enum class BadgeTone {
    Neutral,
    Primary,
    Success,
    Warning,
}

@Composable
private fun Badge(text: String, tone: BadgeTone = BadgeTone.Neutral) {
    val background = when (tone) {
        BadgeTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer
        BadgeTone.Primary -> MaterialTheme.colorScheme.primaryContainer
        BadgeTone.Success -> Color(0xFFDFF7E8)
        BadgeTone.Warning -> Color(0xFFFFF1D6)
    }
    val foreground = when (tone) {
        BadgeTone.Neutral -> MaterialTheme.colorScheme.onSecondaryContainer
        BadgeTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        BadgeTone.Success -> Color(0xFF166534)
        BadgeTone.Warning -> Color(0xFF92400E)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun MessageOverlay(state: ZephyrUiState.Ready, onDismiss: () -> Unit) {
    val message = state.errorMessage ?: state.lastOutcome ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(10_000)
        onDismiss()
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun BusyOverlay(state: ZephyrUiState.Ready) {
    val label = when {
        state.localOnlyScanInProgress -> "Scanning local-only versions"
        state.isCatalogLoading -> "Loading SDKMAN catalog"
        state.isRefreshing -> "Refreshing"
        else -> return
    }
    Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun headerTitle(state: ZephyrUiState.Ready): String =
    when (val route = state.route) {
        is ZephyrRoute.JdkDetail -> state.selectedCandidate?.displayName ?: "JDK"
        is ZephyrRoute.SdkDetail -> state.selectedCandidate?.displayName ?: displayNameFor(route.candidate)
        else -> "Zephyr"
    }

private fun jdkSubtitle(state: ZephyrUiState.Ready): String {
    val current = state.jdkSelection.currentIdentifier
    val default = state.jdkSelection.defaultIdentifier
    return when {
        current != null && default != null && current != default -> "Current JDK: $current - Default: $default"
        current != null -> "Current JDK: $current"
        default != null -> "Default JDK: $default"
        else -> "No JDK Installed"
    }
}

private fun sdkmanVersionLabel(state: ZephyrUiState.Ready): String =
    state.sdkmanStatus.cliVersion ?: "SDKMAN version unknown"

private fun statusText(version: CandidateVersion): String =
    buildList {
        if (version.isCurrent) add("Current")
        if (version.isInstalled) add("Installed")
        if (version.isRemoteAvailable) add("Available") else add("Local only")
    }.joinToString(" - ")

private fun List<CandidateVersion>.updateTargets(): List<CandidateVersion> =
    filter { it.isRemoteAvailable && !it.isInstalled }

private fun selfUpdateLabel(status: SdkmanSelfUpdateStatus): String =
    when (status) {
        SdkmanSelfUpdateStatus.NotChecked -> "SDKMAN updates: not checked"
        SdkmanSelfUpdateStatus.UpToDate -> "SDKMAN updates: up to date"
        SdkmanSelfUpdateStatus.Updated -> "SDKMAN updates: updated"
        is SdkmanSelfUpdateStatus.Failed -> "SDKMAN updates: failed"
    }

private fun selfUpdateShortLabel(status: SdkmanSelfUpdateStatus): String =
    when (status) {
        SdkmanSelfUpdateStatus.NotChecked -> "not checked"
        SdkmanSelfUpdateStatus.UpToDate -> "up to date"
        SdkmanSelfUpdateStatus.Updated -> "updated"
        is SdkmanSelfUpdateStatus.Failed -> "failed"
    }

private fun metadataLabel(status: CandidateMetadataStatus): String =
    when (status) {
        CandidateMetadataStatus.NotChecked -> "Metadata: not checked"
        CandidateMetadataStatus.Refreshing -> "Metadata: refreshing"
        CandidateMetadataStatus.Refreshed -> "Metadata: refreshed"
        is CandidateMetadataStatus.Failed -> "Metadata: failed"
    }

private fun metadataShortLabel(status: CandidateMetadataStatus): String =
    when (status) {
        CandidateMetadataStatus.NotChecked -> "not checked"
        CandidateMetadataStatus.Refreshing -> "refreshing"
        CandidateMetadataStatus.Refreshed -> "refreshed"
        is CandidateMetadataStatus.Failed -> "failed"
    }
