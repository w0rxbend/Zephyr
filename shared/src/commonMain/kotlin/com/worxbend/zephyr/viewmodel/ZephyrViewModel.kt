package com.worxbend.zephyr.viewmodel

import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.JdkSelection
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.logging.ZephyrLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ZephyrRoute {
    data object InstalledJdk : ZephyrRoute
    data object InstalledSdks : ZephyrRoute
    data object BrowseJdks : ZephyrRoute
    data object BrowseSdks : ZephyrRoute
    data object LocalOnly : ZephyrRoute
    data class JdkDetail(val candidate: String = "java") : ZephyrRoute
    data class SdkDetail(val candidate: String) : ZephyrRoute
}

sealed interface ZephyrUiState {
    data object Loading : ZephyrUiState
    data class SdkmanMissing(val message: String) : ZephyrUiState
    data class Ready(
        val sdkmanStatus: SdkmanStatus,
        val jdkSelection: JdkSelection,
        val route: ZephyrRoute,
        val previousRoute: ZephyrRoute?,
        val candidates: List<Candidate>,
        val catalog: List<CandidateCatalogItem>,
        val selectedCandidate: Candidate?,
        val isRefreshing: Boolean,
        val isCatalogLoading: Boolean,
        val localOnlyScanInProgress: Boolean,
        val errorMessage: String?,
        val lastOutcome: String?,
    ) : ZephyrUiState
}

class ZephyrViewModel(
    private val repository: SdkmanRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ZephyrUiState>(ZephyrUiState.Loading)
    val state: StateFlow<ZephyrUiState> = _state

    init {
        refreshAll()
    }

    fun refreshAll() {
        scope.launch {
            _state.value = ZephyrUiState.Loading
            runCatching {
                val detected = repository.detect()
                if (!detected.isInstalled) {
                    _state.value = ZephyrUiState.SdkmanMissing(detected.reason ?: "SDKMAN could not be found.")
                    return@launch
                }

                val version = repository.cliVersion()
                val status = detected.copy(cliVersion = version)
                val candidates = repository.installedCandidates()
                val catalog = repository.catalog(refreshMetadata = true)
                _state.value = ZephyrUiState.Ready(
                    sdkmanStatus = status.copy(metadataStatus = CandidateMetadataStatus.Refreshed),
                    jdkSelection = repository.jdkSelection(),
                    route = ZephyrRoute.InstalledJdk,
                    previousRoute = null,
                    candidates = candidates,
                    catalog = catalog,
                    selectedCandidate = null,
                    isRefreshing = false,
                    isCatalogLoading = false,
                    localOnlyScanInProgress = false,
                    errorMessage = null,
                    lastOutcome = "Loaded ${catalog.size} SDKMAN packages.",
                )
            }.onFailure { failure ->
                ZephyrLogger.error("Initial SDKMAN load failed.", failure)
                runCatching {
                    val detected = repository.detect()
                    if (detected.isInstalled) {
                        _state.value = ZephyrUiState.Ready(
                            sdkmanStatus = detected.copy(cliVersion = repository.cliVersion()),
                            jdkSelection = repository.jdkSelection(),
                            route = ZephyrRoute.BrowseSdks,
                            previousRoute = null,
                            candidates = repository.installedCandidates(),
                            catalog = emptyList(),
                            selectedCandidate = null,
                            isRefreshing = false,
                            isCatalogLoading = false,
                            localOnlyScanInProgress = false,
                            errorMessage = "SDKMAN catalog failed: ${failure.message}",
                            lastOutcome = null,
                        )
                    } else {
                        _state.value = ZephyrUiState.SdkmanMissing(detected.reason ?: failure.message ?: "SDKMAN could not be found.")
                    }
                }.onFailure { fallbackFailure ->
                    ZephyrLogger.error("Failed to build fallback UI after SDKMAN load failure.", fallbackFailure)
                    _state.value = ZephyrUiState.SdkmanMissing(failure.message ?: "SDKMAN could not be loaded.")
                }
            }
        }
    }

    fun navigate(route: ZephyrRoute) {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        _state.value = ready.copy(
            route = route,
            previousRoute = if (route is ZephyrRoute.JdkDetail || route is ZephyrRoute.SdkDetail) ready.route else null,
            selectedCandidate = null,
            errorMessage = null,
        )
        when (route) {
            is ZephyrRoute.JdkDetail -> loadDetail(route.candidate)
            is ZephyrRoute.SdkDetail -> loadDetail(route.candidate)
            ZephyrRoute.BrowseJdks -> {
                ensureCatalog()
                loadDetail("java")
            }
            ZephyrRoute.BrowseSdks -> ensureCatalog()
            else -> Unit
        }
    }

    fun goBack() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        _state.value = ready.copy(
            route = ready.previousRoute ?: ZephyrRoute.InstalledJdk,
            previousRoute = null,
            selectedCandidate = null,
            errorMessage = null,
        )
    }

    fun clearMessages() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        _state.value = ready.copy(errorMessage = null, lastOutcome = null)
    }

    fun refreshInstalled() {
        scope.launch {
            val ready = setRefreshing(true) ?: return@launch
            runCatching {
                _state.value = ready.copy(
                    candidates = repository.installedCandidates(),
                    jdkSelection = repository.jdkSelection(),
                    isRefreshing = false,
                    errorMessage = null,
                )
                refreshSelectedDetailIfNeeded()
            }.onFailure {
                ZephyrLogger.warn("Refresh failed.", it)
                fail("Refresh failed: ${it.message}")
            }
        }
    }

    fun refreshMetadata() {
        scope.launch {
            val ready = _state.value as? ZephyrUiState.Ready ?: return@launch
            _state.value = ready.copy(
                sdkmanStatus = ready.sdkmanStatus.copy(metadataStatus = CandidateMetadataStatus.Refreshing),
                isCatalogLoading = true,
            )
            val outcome = repository.refreshCandidateMetadata()
            if (!outcome.success) {
                ZephyrLogger.warn("Candidate metadata refresh failed: ${outcome.message}")
            }
            val metadataStatus = if (outcome.success) {
                CandidateMetadataStatus.Refreshed
            } else {
                CandidateMetadataStatus.Failed(outcome.message)
            }
            val catalog = repository.catalog(refreshMetadata = false)
            _state.updateReady {
                it.copy(
                    sdkmanStatus = it.sdkmanStatus.copy(metadataStatus = metadataStatus),
                    catalog = catalog,
                    isCatalogLoading = false,
                    lastOutcome = "${outcome.message} Loaded ${catalog.size} packages.",
                    errorMessage = if (outcome.success) null else outcome.message,
                )
            }
        }
    }

    fun checkSdkmanUpdates() {
        scope.launch {
            val ready = setRefreshing(true) ?: return@launch
            val result = repository.selfUpdate()
            if (result is SdkmanSelfUpdateStatus.Failed) {
                ZephyrLogger.warn("SDKMAN self-update failed: ${result.message}")
            }
            val version = repository.cliVersion()
            _state.value = ready.copy(
                sdkmanStatus = ready.sdkmanStatus.copy(cliVersion = version, selfUpdateStatus = result),
                isRefreshing = false,
                lastOutcome = when (result) {
                    SdkmanSelfUpdateStatus.NotChecked -> null
                    SdkmanSelfUpdateStatus.UpToDate -> "SDKMAN is up to date."
                    SdkmanSelfUpdateStatus.Updated -> "SDKMAN was updated."
                    is SdkmanSelfUpdateStatus.Failed -> result.message
                },
                errorMessage = (result as? SdkmanSelfUpdateStatus.Failed)?.message,
            )
        }
    }

    fun scanLocalOnly() {
        scope.launch {
            val ready = _state.value as? ZephyrUiState.Ready ?: return@launch
            _state.value = ready.copy(localOnlyScanInProgress = true, errorMessage = null)
            runCatching {
                val audited = repository.installedCandidates().map { local ->
                    repository.mergedCandidate(local.name) ?: local
                }
                val jdkSelection = repository.jdkSelection()
                _state.updateReady {
                    it.copy(
                        candidates = audited,
                        localOnlyScanInProgress = false,
                        jdkSelection = jdkSelection,
                        selectedCandidate = it.selectedCandidate?.let { selected ->
                            audited.firstOrNull { candidate -> candidate.name == selected.name } ?: selected
                        },
                    )
                }
            }.onFailure {
                ZephyrLogger.warn("Local-only scan failed.", it)
                fail("Local-only scan failed: ${it.message}")
            }
        }
    }

    fun install(candidate: String, version: String) = mutate {
        repository.install(candidate, version)
    }

    fun uninstall(candidate: String, version: String) = mutate {
        repository.uninstall(candidate, version)
    }

    fun use(candidate: String, version: String) = mutate {
        repository.use(candidate, version)
    }

    fun setDefault(candidate: String, version: String) = mutate {
        repository.setDefault(candidate, version)
    }

    fun cleanLocalOnly(candidate: String, versions: List<String>) = mutate {
        repository.cleanLocalOnly(candidate, versions)
    }

    private fun ensureCatalog() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        if (ready.catalog.isNotEmpty()) return
        scope.launch {
            _state.updateReady { it.copy(isCatalogLoading = true) }
            runCatching {
                val catalog = repository.catalog(refreshMetadata = true)
                _state.updateReady { it.copy(catalog = catalog, isCatalogLoading = false, lastOutcome = "Loaded ${catalog.size} SDKMAN packages.") }
            }.onFailure {
                ZephyrLogger.warn("Catalog load failed.", it)
                fail("Catalog load failed: ${it.message}")
            }
        }
    }

    private fun loadDetail(candidate: String) {
        scope.launch {
            _state.updateReady { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                val merged = repository.mergedCandidate(candidate)
                _state.updateReady {
                    it.copy(
                        selectedCandidate = merged,
                        isRefreshing = false,
                        candidates = it.candidates.replaceCandidate(merged),
                    )
                }
            }.onFailure {
                ZephyrLogger.warn("Version load failed for $candidate.", it)
                fail("Version load failed: ${it.message}")
            }
        }
    }

    private fun mutate(block: suspend () -> CommandOutcome) {
        scope.launch {
            val ready = setRefreshing(true) ?: return@launch
            val outcome = block()
            if (!outcome.success) {
                ZephyrLogger.warn("SDKMAN mutation failed: ${outcome.message}")
            }
            val candidates = repository.installedCandidates()
            val selectedName = ready.selectedCandidate?.name
            val selected = selectedName?.let { repository.mergedCandidate(it) }
            _state.value = ready.copy(
                candidates = candidates.replaceCandidate(selected),
                selectedCandidate = selected,
                jdkSelection = repository.jdkSelection(),
                isRefreshing = false,
                lastOutcome = outcome.message,
                errorMessage = if (outcome.success) null else outcome.message,
            )
        }
    }

    private suspend fun refreshSelectedDetailIfNeeded() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        val selected = ready.selectedCandidate ?: return
        _state.value = ready.copy(selectedCandidate = repository.mergedCandidate(selected.name))
    }

    private fun setRefreshing(refreshing: Boolean): ZephyrUiState.Ready? {
        val ready = _state.value as? ZephyrUiState.Ready ?: return null
        _state.value = ready.copy(isRefreshing = refreshing, errorMessage = null, lastOutcome = null)
        return ready
    }

    private fun fail(message: String) {
        ZephyrLogger.warn(message)
        _state.updateReady {
            it.copy(
                isRefreshing = false,
                isCatalogLoading = false,
                localOnlyScanInProgress = false,
                errorMessage = message,
            )
        }
    }
}

private fun MutableStateFlow<ZephyrUiState>.updateReady(transform: (ZephyrUiState.Ready) -> ZephyrUiState.Ready) {
    update { state ->
        if (state is ZephyrUiState.Ready) transform(state) else state
    }
}

private fun List<Candidate>.replaceCandidate(candidate: Candidate?): List<Candidate> {
    if (candidate == null) return this
    val index = indexOfFirst { it.name == candidate.name }
    return if (index < 0) this + candidate else toMutableList().also { it[index] = candidate }
}
