package com.worxbend.zephyr.viewmodel

import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.withInstalledCandidates
import com.worxbend.zephyr.logging.ZephyrLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ZephyrRoute {
    data object Overview : ZephyrRoute
    data object InstalledJdk : ZephyrRoute
    data object InstalledSdks : ZephyrRoute
    data object BrowseJdks : ZephyrRoute
    data object BrowseSdks : ZephyrRoute
    data object LocalOnly : ZephyrRoute
    data object Diagnostics : ZephyrRoute
    data object Settings : ZephyrRoute
    data object About : ZephyrRoute
    data class JdkDetail(val candidate: String = "java") : ZephyrRoute
    data class SdkDetail(val candidate: String) : ZephyrRoute
}

sealed interface ZephyrUiState {
    data object Loading : ZephyrUiState
    data class SdkmanMissing(val message: String) : ZephyrUiState
    data class Ready(
        val sdkmanStatus: SdkmanStatus,
        val route: ZephyrRoute,
        val previousRoute: ZephyrRoute?,
        val candidates: List<Candidate>,
        val catalog: List<CandidateCatalogItem>,
        val selectedCandidate: Candidate?,
        val isRefreshing: Boolean,
        val isCatalogLoading: Boolean,
        val localOnlyScanInProgress: Boolean,
        val detailLoadingCandidate: String? = null,
        val errorMessage: String?,
        val lastOutcome: String?,
    ) : ZephyrUiState
}

class ZephyrViewModel(
    private val repository: SdkmanRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<ZephyrUiState>(ZephyrUiState.Loading)
    val state: StateFlow<ZephyrUiState> = _state
    private val operationMutex = Mutex()

    init {
        refreshAll()
    }

    fun close() {
        scope.cancel()
    }

    fun refreshAll() {
        launchOperation {
            _state.value = ZephyrUiState.Loading
            runCatchingCancellable {
                val detected = repository.detect()
                if (!detected.isInstalled) {
                    _state.value = ZephyrUiState.SdkmanMissing(detected.reason ?: "SDKMAN could not be found.")
                } else {
                    val version = repository.cliVersion()
                    val status = detected.copy(cliVersion = version)
                    val candidates = repository.installedCandidates()
                    _state.value = ZephyrUiState.Ready(
                        sdkmanStatus = status,
                        route = ZephyrRoute.Overview,
                        previousRoute = null,
                        candidates = candidates,
                        catalog = emptyList(),
                        selectedCandidate = null,
                        isRefreshing = false,
                        isCatalogLoading = false,
                        localOnlyScanInProgress = false,
                        errorMessage = null,
                        lastOutcome = "Loaded ${candidates.size} installed SDKMAN package(s).",
                    )
                }
            }.onFailure { failure ->
                ZephyrLogger.error("Initial SDKMAN load failed.", failure)
                runCatchingCancellable {
                    val detected = repository.detect()
                    if (detected.isInstalled) {
                        _state.value = ZephyrUiState.Ready(
                            sdkmanStatus = detected.copy(cliVersion = repository.cliVersion()),
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
        if (_state.value !is ZephyrUiState.Ready) return
        _state.updateReady { ready ->
            ready.copy(
                route = route,
                previousRoute = if (route is ZephyrRoute.JdkDetail || route is ZephyrRoute.SdkDetail) ready.route else null,
                selectedCandidate = null,
                detailLoadingCandidate = null,
                errorMessage = null,
            )
        }
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
        if (_state.value !is ZephyrUiState.Ready) return
        _state.updateReady { ready ->
            ready.copy(
                route = ready.previousRoute ?: ZephyrRoute.Overview,
                previousRoute = null,
                selectedCandidate = null,
                detailLoadingCandidate = null,
                errorMessage = null,
            )
        }
    }

    fun clearMessages() {
        _state.updateReady { it.copy(errorMessage = null, lastOutcome = null) }
    }

    fun refreshInstalled() {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val candidates = repository.installedCandidates()
                _state.updateReady {
                    it.copy(
                        candidates = candidates,
                        catalog = it.catalog.withInstalledCandidates(candidates),
                        isRefreshing = false,
                        errorMessage = null,
                    )
                }
                refreshSelectedDetailIfNeeded()
            }.onFailure {
                ZephyrLogger.warn("Refresh failed.", it)
                fail("Refresh failed: ${it.message}")
            }
        }
    }

    fun refreshMetadata() {
        launchOperation {
            if (_state.value !is ZephyrUiState.Ready) return@launchOperation
            _state.updateReady { ready ->
                ready.copy(
                    sdkmanStatus = ready.sdkmanStatus.copy(metadataStatus = CandidateMetadataStatus.Refreshing),
                    isCatalogLoading = true,
                )
            }
            runCatchingCancellable {
                val outcome = repository.refreshCandidateMetadata()
                if (!outcome.success) ZephyrLogger.warn("Candidate metadata refresh failed: ${outcome.message}")
                outcome to repository.catalog(refreshMetadata = false)
            }.onSuccess { (outcome, catalog) ->
                val metadataStatus = if (outcome.success) CandidateMetadataStatus.Refreshed else CandidateMetadataStatus.Failed(outcome.message)
                _state.updateReady {
                    it.copy(
                        sdkmanStatus = it.sdkmanStatus.copy(metadataStatus = metadataStatus),
                        catalog = catalog,
                        isCatalogLoading = false,
                        lastOutcome = "${outcome.message} Loaded ${catalog.size} packages.",
                        errorMessage = if (outcome.success) null else outcome.message,
                    )
                }
            }.onFailure {
                ZephyrLogger.warn("Candidate metadata refresh failed.", it)
                _state.updateReady { state ->
                    state.copy(
                        sdkmanStatus = state.sdkmanStatus.copy(metadataStatus = CandidateMetadataStatus.Failed(it.message.orEmpty())),
                        isCatalogLoading = false,
                        errorMessage = "Candidate metadata refresh failed: ${it.message}",
                    )
                }
            }
        }
    }

    fun checkSdkmanUpdates() {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val result = repository.selfUpdate()
                result to repository.cliVersion()
            }.onSuccess { (result, version) ->
                if (result is SdkmanSelfUpdateStatus.Failed) ZephyrLogger.warn("SDKMAN self-update failed: ${result.message}")
                _state.updateReady {
                    it.copy(
                        sdkmanStatus = it.sdkmanStatus.copy(cliVersion = version, selfUpdateStatus = result),
                        isRefreshing = false,
                        lastOutcome = result.outcomeMessage(),
                        errorMessage = (result as? SdkmanSelfUpdateStatus.Failed)?.message,
                    )
                }
            }.onFailure {
                ZephyrLogger.warn("SDKMAN self-update failed.", it)
                fail("SDKMAN self-update failed: ${it.message}")
            }
        }
    }

    fun scanLocalOnly() {
        launchOperation {
            if (_state.value !is ZephyrUiState.Ready) return@launchOperation
            _state.updateReady { it.copy(localOnlyScanInProgress = true, errorMessage = null) }
            runCatchingCancellable {
                val audited = repository.installedCandidates().map { local ->
                    repository.mergedCandidate(local.name) ?: local
                }
                _state.updateReady {
                    it.copy(
                        candidates = audited,
                        localOnlyScanInProgress = false,
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

    fun setDefault(candidate: String, version: String) = mutate {
        repository.setDefault(candidate, version)
    }

    fun cleanLocalOnly(candidate: String, versions: List<String>) = mutate {
        repository.cleanLocalOnly(candidate, versions)
    }

    private fun ensureCatalog() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        if (ready.catalog.isNotEmpty()) return
        launchQueuedOperation {
            if ((_state.value as? ZephyrUiState.Ready)?.catalog?.isNotEmpty() == true) return@launchQueuedOperation
            _state.updateReady { it.copy(isCatalogLoading = true) }
            runCatchingCancellable {
                val catalog = repository.catalog(refreshMetadata = true)
                _state.updateReady { it.copy(catalog = catalog, isCatalogLoading = false, lastOutcome = "Loaded ${catalog.size} SDKMAN packages.") }
            }.onFailure {
                ZephyrLogger.warn("Catalog load failed.", it)
                fail("Catalog load failed: ${it.message}")
            }
        }
    }

    private fun loadDetail(candidate: String) {
        launchQueuedOperation {
            var shouldLoad = false
            _state.update { state ->
                if (state is ZephyrUiState.Ready && state.displaysCandidate(candidate)) {
                    shouldLoad = true
                    state.copy(detailLoadingCandidate = candidate, errorMessage = null)
                } else {
                    state
                }
            }
            if (!shouldLoad) return@launchQueuedOperation
            runCatchingCancellable {
                val merged = repository.mergedCandidate(candidate)
                _state.updateReady {
                    if (it.displaysCandidate(candidate)) {
                        it.copy(
                            selectedCandidate = merged,
                            detailLoadingCandidate = null,
                            candidates = it.candidates.replaceCandidate(merged),
                        )
                    } else {
                        it
                    }
                }
            }.onFailure {
                if ((_state.value as? ZephyrUiState.Ready)?.displaysCandidate(candidate) == true) {
                    ZephyrLogger.warn("Version load failed for $candidate.", it)
                    fail("Version load failed: ${it.message}")
                }
            }
        }
    }

    private fun mutate(block: suspend () -> CommandOutcome) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val outcome = block()
                val candidates = repository.installedCandidates()
                val selectedName = (_state.value as? ZephyrUiState.Ready)?.selectedCandidate?.name
                val selected = selectedName?.let { repository.mergedCandidate(it) }
                MutationResult(outcome, candidates, selected)
            }.onSuccess { result ->
                if (!result.outcome.success) ZephyrLogger.warn("SDKMAN mutation failed: ${result.outcome.message}")
                _state.updateReady {
                    it.copy(
                        candidates = result.candidates.replaceCandidate(result.selectedCandidate),
                        catalog = it.catalog.withInstalledCandidates(result.candidates),
                        selectedCandidate = result.selectedCandidate,
                        isRefreshing = false,
                        lastOutcome = result.outcome.message,
                        errorMessage = if (result.outcome.success) null else result.outcome.message,
                    )
                }
            }.onFailure {
                ZephyrLogger.warn("SDKMAN mutation failed.", it)
                fail("SDKMAN mutation failed: ${it.message}")
            }
        }
    }

    private suspend fun refreshSelectedDetailIfNeeded() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        val selected = ready.selectedCandidate ?: return
        val refreshed = repository.mergedCandidate(selected.name)
        _state.updateReady { state ->
            if (state.selectedCandidate?.name == selected.name) state.copy(selectedCandidate = refreshed) else state
        }
    }

    private fun beginRefresh(): Boolean {
        if (_state.value !is ZephyrUiState.Ready) return false
        _state.updateReady { it.copy(isRefreshing = true, errorMessage = null, lastOutcome = null) }
        return true
    }

    private fun launchOperation(block: suspend () -> Unit) {
        scope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                block()
            } finally {
                operationMutex.unlock()
            }
        }
    }

    private fun launchQueuedOperation(block: suspend () -> Unit) {
        scope.launch {
            operationMutex.withLock { block() }
        }
    }

    private fun fail(message: String) {
        ZephyrLogger.warn(message)
        _state.updateReady {
            it.copy(
                isRefreshing = false,
                isCatalogLoading = false,
                localOnlyScanInProgress = false,
                detailLoadingCandidate = null,
                errorMessage = message,
            )
        }
    }

    private data class MutationResult(
        val outcome: CommandOutcome,
        val candidates: List<Candidate>,
        val selectedCandidate: Candidate?,
    )
}

private fun SdkmanSelfUpdateStatus.outcomeMessage(): String? =
    when (this) {
        SdkmanSelfUpdateStatus.NotChecked -> null
        SdkmanSelfUpdateStatus.UpToDate -> "SDKMAN is up to date."
        SdkmanSelfUpdateStatus.Updated -> "SDKMAN was updated."
        is SdkmanSelfUpdateStatus.Failed -> message
    }

private fun MutableStateFlow<ZephyrUiState>.updateReady(transform: (ZephyrUiState.Ready) -> ZephyrUiState.Ready) {
    update { state ->
        if (state is ZephyrUiState.Ready) transform(state) else state
    }
}

private suspend inline fun <T> runCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

private fun List<Candidate>.replaceCandidate(candidate: Candidate?): List<Candidate> {
    if (candidate == null) return this
    val index = indexOfFirst { it.name == candidate.name }
    return if (index < 0) this else toMutableList().also { it[index] = candidate }
}

private fun ZephyrUiState.Ready.displaysCandidate(candidate: String): Boolean =
    when (val currentRoute = route) {
        is ZephyrRoute.JdkDetail -> currentRoute.candidate == candidate
        is ZephyrRoute.SdkDetail -> currentRoute.candidate == candidate
        ZephyrRoute.BrowseJdks -> candidate == "java"
        else -> false
    }
