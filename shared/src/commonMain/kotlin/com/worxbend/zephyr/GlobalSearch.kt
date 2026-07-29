package com.worxbend.zephyr

import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.viewmodel.ZephyrRoute

internal enum class GlobalSearchKind(val label: String) {
    Page("Page"),
    Candidate("Candidate"),
    Version("Version"),
    Setting("Setting"),
    Action("Action"),
}

internal enum class GlobalSearchAction {
    RefreshInstalled,
    ScanLocalOnly,
    RefreshConnectivity,
    RefreshMetadata,
    CheckUpdates,
}

internal sealed interface GlobalSearchTarget {
    data class Navigate(val route: ZephyrRoute) : GlobalSearchTarget
    data class Execute(val action: GlobalSearchAction) : GlobalSearchTarget
}

internal data class GlobalSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: GlobalSearchKind,
    val target: GlobalSearchTarget,
    val terms: List<String> = emptyList(),
)

internal fun buildGlobalSearchIndex(
    candidates: List<Candidate>,
    catalog: List<CandidateCatalogItem>,
): List<GlobalSearchItem> = buildList {
    addPageItems()
    addSettingItems()
    addActionItems()

    val installedByName = candidates.associateBy(Candidate::name)
    val catalogByName = catalog.associateBy(CandidateCatalogItem::name)
    (installedByName.keys + catalogByName.keys)
        .sorted()
        .forEach { name ->
            val installed = installedByName[name]
            val remote = catalogByName[name]
            val kind = installed?.kind ?: remote?.kind ?: CandidateKind.Sdk
            val displayName = installed?.displayName ?: remote?.displayName ?: name
            val installedCount = installed?.installedVersions?.count { it.isInstalled } ?: 0
            val status = if (installed != null) {
                "$installedCount installed version(s)"
            } else {
                "Available from SDKMAN"
            }
            add(
                GlobalSearchItem(
                    id = "candidate:$name",
                    title = displayName,
                    subtitle = "${kind.searchLabel} • $status",
                    kind = GlobalSearchKind.Candidate,
                    target = GlobalSearchTarget.Navigate(kind.detailRoute(name)),
                    terms = listOfNotNull(name, installed?.description, remote?.description, remote?.stableVersion),
                ),
            )
        }

    candidates.forEach { candidate ->
        candidate.installedVersions.forEach { version ->
            val states = buildList {
                if (version.isDefault) add("Default")
                if (version.isInstalled) add("Installed")
                if (version.isRemoteAvailable) add("Available")
                if (!version.isRemoteAvailable) add("Local-only")
            }
            add(
                GlobalSearchItem(
                    id = "version:${candidate.name}:${version.version}",
                    title = version.version,
                    subtitle = "${candidate.displayName} • ${states.joinToString(" • ")}",
                    kind = GlobalSearchKind.Version,
                    target = GlobalSearchTarget.Navigate(candidate.kind.detailRoute(candidate.name)),
                    terms = listOf(candidate.name, candidate.displayName) + states,
                ),
            )
        }
    }
}

internal fun searchGlobalIndex(
    items: List<GlobalSearchItem>,
    query: String,
    limit: Int = 10,
): List<GlobalSearchItem> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return items.take(limit)
    return items
        .mapNotNull { item -> item.matchScore(normalized)?.let { score -> score to item } }
        .sortedWith(
            compareBy<Pair<Int, GlobalSearchItem>> { it.first }
                .thenBy { it.second.kind.ordinal }
                .thenBy { it.second.title.lowercase() },
        )
        .take(limit)
        .map(Pair<Int, GlobalSearchItem>::second)
}

private fun MutableList<GlobalSearchItem>.addPageItems() {
    listOf(
        "Overview" to ZephyrRoute.Overview,
        "Installed JDK" to ZephyrRoute.InstalledJdk,
        "Installed SDKs" to ZephyrRoute.InstalledSdks,
        "Browse JDKs" to ZephyrRoute.BrowseJdks,
        "Browse SDKs" to ZephyrRoute.BrowseSdks,
        "Local-only versions" to ZephyrRoute.LocalOnly,
        "Diagnostics" to ZephyrRoute.Diagnostics,
        "Operation history" to ZephyrRoute.History,
        "Settings" to ZephyrRoute.Settings,
        "About Zephyr" to ZephyrRoute.About,
    ).forEach { (title, route) ->
        add(
            GlobalSearchItem(
                id = "page:${title.lowercase()}",
                title = title,
                subtitle = "Open workspace destination",
                kind = GlobalSearchKind.Page,
                target = GlobalSearchTarget.Navigate(route),
            ),
        )
    }
}

private fun MutableList<GlobalSearchItem>.addSettingItems() {
    listOf(
        Triple("Theme preference", "Choose System, Light, or Dark appearance", listOf("appearance", "color")),
        Triple("Information density", "Choose Compact or Comfortable spacing", listOf("ui", "layout", "spacing")),
        Triple("SDKMAN path privacy", "Show or hide the SDKMAN home path", listOf("home", "security")),
    ).forEach { (title, subtitle, terms) ->
        add(
            GlobalSearchItem(
                id = "setting:${title.lowercase()}",
                title = title,
                subtitle = subtitle,
                kind = GlobalSearchKind.Setting,
                target = GlobalSearchTarget.Navigate(ZephyrRoute.Settings),
                terms = terms,
            ),
        )
    }
}

private fun MutableList<GlobalSearchItem>.addActionItems() {
    listOf(
        Triple("Refresh local state", "Reload installed SDKMAN candidates", GlobalSearchAction.RefreshInstalled),
        Triple("Scan local-only versions", "Audit installed versions against SDKMAN", GlobalSearchAction.ScanLocalOnly),
        Triple("Check connectivity", "Probe SDKMAN service reachability", GlobalSearchAction.RefreshConnectivity),
        Triple("Refresh SDKMAN metadata", "Review a remote metadata refresh", GlobalSearchAction.RefreshMetadata),
        Triple("Check for SDKMAN updates", "Review an SDKMAN self-update check", GlobalSearchAction.CheckUpdates),
    ).forEach { (title, subtitle, action) ->
        add(
            GlobalSearchItem(
                id = "action:${action.name}",
                title = title,
                subtitle = subtitle,
                kind = GlobalSearchKind.Action,
                target = GlobalSearchTarget.Execute(action),
            ),
        )
    }
}

private fun GlobalSearchItem.matchScore(query: String): Int? {
    val normalizedTitle = title.lowercase()
    val searchableTerms = listOf(subtitle) + terms
    return when {
        normalizedTitle == query -> 0
        normalizedTitle.startsWith(query) -> 1
        normalizedTitle.contains(query) -> 2
        searchableTerms.any { it.lowercase().startsWith(query) } -> 3
        searchableTerms.any { it.lowercase().contains(query) } -> 4
        else -> null
    }
}

private val CandidateKind.searchLabel: String
    get() = if (this == CandidateKind.Jdk) "JDK" else "SDK"

private fun CandidateKind.detailRoute(candidate: String): ZephyrRoute =
    if (this == CandidateKind.Jdk) ZephyrRoute.JdkDetail(candidate) else ZephyrRoute.SdkDetail(candidate)
