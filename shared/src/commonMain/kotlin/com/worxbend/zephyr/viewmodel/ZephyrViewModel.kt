package com.worxbend.zephyr.viewmodel

import com.worxbend.zephyr.data.DiagnosticsExporter
import com.worxbend.zephyr.data.OperationJournalExporter
import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.data.createDiagnosticsExporter
import com.worxbend.zephyr.data.createOperationJournalExporter
import com.worxbend.zephyr.data.currentEpochMillis
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateMetadataStatus
import com.worxbend.zephyr.domain.BatchInstallProgress
import com.worxbend.zephyr.domain.BatchItemStatus
import com.worxbend.zephyr.domain.BatchUninstallProgress
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.ConnectivityState
import com.worxbend.zephyr.domain.ConnectivityStatus
import com.worxbend.zephyr.domain.DiskImpactEstimate
import com.worxbend.zephyr.domain.DiskImpactKind
import com.worxbend.zephyr.domain.DiagnosticsSnapshot
import com.worxbend.zephyr.domain.EstimateConfidence
import com.worxbend.zephyr.domain.IntegrityCheck
import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.ReadRetryStatus
import com.worxbend.zephyr.domain.RetryableReadOperation
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanCommandAction
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.SnapshotRestoreProgress
import com.worxbend.zephyr.domain.requiresNetwork
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
import kotlinx.coroutines.delay
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
    data object UpdateCenter : ZephyrRoute
    data object BatchUninstall : ZephyrRoute
    data object Profiles : ZephyrRoute
    data object ProjectImport : ZephyrRoute
    data object ProjectExport : ZephyrRoute
    data object EnvironmentSnapshot : ZephyrRoute
    data object Comparison : ZephyrRoute
    data object Diagnostics : ZephyrRoute
    data object History : ZephyrRoute
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
        val catalogCachedAtEpochMillis: Long? = null,
        val catalogIsCached: Boolean = false,
        val selectedCandidate: Candidate?,
        val isRefreshing: Boolean,
        val isCatalogLoading: Boolean,
        val localOnlyScanInProgress: Boolean,
        val detailLoadingCandidate: String? = null,
        val errorMessage: String?,
        val lastOutcome: String?,
        val pendingTransaction: SdkmanTransaction? = null,
        val pendingTransactionDiskImpact: DiskImpactEstimate? = null,
        val transactionPreviewLoading: Boolean = false,
        val operationJournal: List<OperationJournalEntry> = emptyList(),
        val journalExportInProgress: Boolean = false,
        val diagnosticsExportInProgress: Boolean = false,
        val batchInstallProgress: List<BatchInstallProgress> = emptyList(),
        val batchUninstallProgress: List<BatchUninstallProgress> = emptyList(),
        val snapshotRestoreProgress: List<SnapshotRestoreProgress> = emptyList(),
        val protectedVersions: Set<ProtectedVersion> = emptySet(),
        val connectivityStatus: ConnectivityStatus = ConnectivityStatus(ConnectivityState.Unknown),
        val integrityChecks: List<IntegrityCheck> = emptyList(),
        val readRetryStatus: ReadRetryStatus? = null,
    ) : ZephyrUiState
}

class ZephyrViewModel(
    private val repository: SdkmanRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val journalExporter: OperationJournalExporter = createOperationJournalExporter(),
    private val diagnosticsExporter: DiagnosticsExporter = createDiagnosticsExporter(),
    private val readRetryDelaysMillis: List<Long> = listOf(500L, 1_500L),
    private val clock: () -> Long = ::currentEpochMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<ZephyrUiState>(ZephyrUiState.Loading)
    val state: StateFlow<ZephyrUiState> = _state
    private val operationMutex = Mutex()
    private var nextJournalId = 1L

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
                    val cachedCatalog = repository.cachedCatalog()
                    val protectedVersions = loadProtectedVersions()
                    val integrityChecks = loadIntegrityChecks()
                    _state.value = ZephyrUiState.Ready(
                        sdkmanStatus = status,
                        route = ZephyrRoute.Overview,
                        previousRoute = null,
                        candidates = candidates,
                        catalog = cachedCatalog?.items.orEmpty().withInstalledCandidates(candidates),
                        catalogCachedAtEpochMillis = cachedCatalog?.cachedAtEpochMillis,
                        catalogIsCached = cachedCatalog != null,
                        selectedCandidate = null,
                        isRefreshing = false,
                        isCatalogLoading = false,
                        localOnlyScanInProgress = false,
                        errorMessage = null,
                        lastOutcome = "Loaded ${candidates.size} installed SDKMAN package(s).",
                        protectedVersions = protectedVersions,
                        integrityChecks = integrityChecks,
                    )
                    refreshConnectivity()
                }
            }.onFailure { failure ->
                ZephyrLogger.error("Initial SDKMAN load failed.", failure)
                runCatchingCancellable {
                    val detected = repository.detect()
                    if (detected.isInstalled) {
                        val candidates = repository.installedCandidates()
                        val cachedCatalog = repository.cachedCatalog()
                        _state.value = ZephyrUiState.Ready(
                            sdkmanStatus = detected.copy(cliVersion = repository.cliVersion()),
                            route = ZephyrRoute.BrowseSdks,
                            previousRoute = null,
                            candidates = candidates,
                            catalog = cachedCatalog?.items.orEmpty().withInstalledCandidates(candidates),
                            catalogCachedAtEpochMillis = cachedCatalog?.cachedAtEpochMillis,
                            catalogIsCached = cachedCatalog != null,
                            selectedCandidate = null,
                            isRefreshing = false,
                            isCatalogLoading = false,
                            localOnlyScanInProgress = false,
                            errorMessage = "SDKMAN catalog failed: ${failure.message}",
                            lastOutcome = null,
                            protectedVersions = loadProtectedVersions(),
                            integrityChecks = loadIntegrityChecks(),
                        )
                        refreshConnectivity()
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
            ZephyrRoute.UpdateCenter -> ensureCatalog()
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

    fun requestTransaction(transaction: SdkmanTransaction) {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        if (ready.hasActiveOperation() || ready.pendingTransaction != null) return
        _state.updateReady {
            it.copy(
                transactionPreviewLoading = true,
                pendingTransactionDiskImpact = null,
            )
        }
        scope.launch {
            if (transaction.requiresNetwork && !checkOnline()) {
                _state.updateReady {
                    it.copy(
                        transactionPreviewLoading = false,
                        errorMessage = "This operation requires the SDKMAN service, but Zephyr is offline.",
                    )
                }
                return@launch
            }
            val estimate = runCatchingCancellable {
                repository.estimateDiskImpact(transaction)
            }.getOrElse { failure ->
                ZephyrLogger.warn("Unable to estimate transaction disk impact.", failure)
                DiskImpactEstimate(
                    kind = DiskImpactKind.Unknown,
                    confidence = EstimateConfidence.Unknown,
                    explanation = "Disk impact could not be measured: ${failure.message ?: "unknown error"}.",
                )
            }
            _state.updateReady {
                it.copy(
                    pendingTransaction = transaction,
                    pendingTransactionDiskImpact = estimate,
                    transactionPreviewLoading = false,
                )
            }
        }
    }

    fun refreshConnectivity() {
        if (_state.value !is ZephyrUiState.Ready) return
        _state.updateReady {
            it.copy(connectivityStatus = it.connectivityStatus.copy(state = ConnectivityState.Checking))
        }
        scope.launch {
            checkOnline()
        }
    }

    fun refreshIntegrity() {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                retryRead(RetryableReadOperation.IntegrityChecks) {
                    repository.integrityChecks()
                }
            }.onSuccess { checks ->
                _state.updateReady {
                    it.copy(
                        integrityChecks = checks,
                        isRefreshing = false,
                        lastOutcome = "SDKMAN integrity checks completed.",
                        errorMessage = null,
                    )
                }
            }.onFailure { failure ->
                ZephyrLogger.warn("SDKMAN integrity checks failed.", failure)
                fail("SDKMAN integrity checks failed: ${failure.message}")
            }
        }
    }

    fun retryTransaction(transaction: SdkmanTransaction) {
        val retry = if (transaction is SdkmanTransaction.CleanLocalOnly) {
            val verified = (_state.value as? ZephyrUiState.Ready)
                ?.candidates
                ?.firstOrNull { it.name == transaction.candidate }
                ?.localOnlyVersions
                .orEmpty()
                .toSet()
            val remaining = transaction.versions.filter { it in verified }
            if (remaining.isEmpty()) {
                _state.updateReady {
                    it.copy(lastOutcome = "Scan local-only versions again before retrying cleanup.")
                }
                return
            }
            transaction.copy(versions = remaining)
        } else {
            transaction
        }
        requestTransaction(retry)
    }

    fun dismissTransaction() {
        _state.updateReady {
            it.copy(
                pendingTransaction = null,
                pendingTransactionDiskImpact = null,
                transactionPreviewLoading = false,
            )
        }
    }

    fun confirmTransaction() {
        val transaction = (_state.value as? ZephyrUiState.Ready)?.pendingTransaction ?: return
        _state.updateReady {
            it.copy(
                pendingTransaction = null,
                pendingTransactionDiskImpact = null,
            )
        }
        val journalId = startJournalEntry(transaction)
        when (transaction) {
            is SdkmanTransaction.Install -> install(transaction.candidate, transaction.version, journalId)
            is SdkmanTransaction.BatchInstall -> batchInstall(transaction, journalId)
            is SdkmanTransaction.SnapshotRestore -> restoreSnapshot(transaction, journalId)
            is SdkmanTransaction.Uninstall -> uninstall(transaction.candidate, transaction.version, journalId)
            is SdkmanTransaction.BatchUninstall -> batchUninstall(transaction, journalId)
            is SdkmanTransaction.SetDefault -> setDefault(transaction.candidate, transaction.version, journalId)
            is SdkmanTransaction.CleanLocalOnly -> cleanLocalOnly(transaction.candidate, transaction.versions, journalId)
            SdkmanTransaction.RefreshMetadata -> refreshMetadata(journalId)
            SdkmanTransaction.SelfUpdate -> checkSdkmanUpdates(journalId)
        }
    }

    fun exportJournal() {
        val entries = (_state.value as? ZephyrUiState.Ready)?.operationJournal.orEmpty()
        if (entries.isEmpty()) {
            _state.updateReady { it.copy(lastOutcome = "The operation journal is empty.") }
            return
        }
        scope.launch {
            _state.updateReady { it.copy(journalExportInProgress = true, errorMessage = null) }
            runCatchingCancellable {
                journalExporter.export(entries)
            }.onSuccess { result ->
                _state.updateReady {
                    it.copy(
                        journalExportInProgress = false,
                        lastOutcome = "Exported ${result.exportedEntries} journal entries to ${result.path}.",
                    )
                }
            }.onFailure { failure ->
                ZephyrLogger.warn("Operation journal export failed.", failure)
                _state.updateReady {
                    it.copy(
                        journalExportInProgress = false,
                        errorMessage = "Operation journal export failed: ${failure.message}",
                    )
                }
            }
        }
    }

    fun exportDiagnostics() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        val snapshot = DiagnosticsSnapshot(
            generatedAtEpochMillis = clock(),
            sdkmanStatus = ready.sdkmanStatus,
            connectivityStatus = ready.connectivityStatus,
            integrityChecks = ready.integrityChecks,
            installedCandidates = ready.candidates.size,
            installedVersions = ready.candidates.sumOf { candidate ->
                candidate.installedVersions.count { it.isInstalled }
            },
            localOnlyVersions = ready.candidates.sumOf { it.localOnlyVersionCount },
            protectedVersions = ready.protectedVersions.size,
            journal = ready.operationJournal,
        )
        scope.launch {
            _state.updateReady { it.copy(diagnosticsExportInProgress = true, errorMessage = null) }
            runCatchingCancellable {
                diagnosticsExporter.export(snapshot)
            }.onSuccess { result ->
                _state.updateReady {
                    it.copy(
                        diagnosticsExportInProgress = false,
                        lastOutcome = "Exported a redacted support bundle to ${result.path}.",
                    )
                }
            }.onFailure { failure ->
                ZephyrLogger.warn("Support bundle export failed.", failure)
                _state.updateReady {
                    it.copy(
                        diagnosticsExportInProgress = false,
                        errorMessage = "Support bundle export failed: ${failure.message}",
                    )
                }
            }
        }
    }

    fun setVersionProtected(candidate: String, version: String, protected: Boolean) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val outcome = repository.setVersionProtected(candidate, version, protected)
                outcome to repository.protectedVersions()
            }.onSuccess { (outcome, protectedVersions) ->
                _state.updateReady {
                    it.copy(
                        protectedVersions = protectedVersions,
                        isRefreshing = false,
                        lastOutcome = outcome.message,
                        errorMessage = if (outcome.success) null else outcome.message,
                    )
                }
            }.onFailure { failure ->
                ZephyrLogger.warn("Protected-version update failed.", failure)
                fail("Protected-version update failed: ${failure.message}")
            }
        }
    }

    fun refreshInstalled() {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val candidates = retryRead(RetryableReadOperation.InstalledCandidates) {
                    repository.installedCandidates()
                }
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

    fun refreshMetadata(journalId: Long? = null) {
        launchOperation {
            refreshMetadataLocked(journalId, scheduled = false)
        }
    }

    fun refreshMetadataIfIdle() {
        launchOperation {
            val ready = _state.value as? ZephyrUiState.Ready ?: return@launchOperation
            if (ready.hasActiveOperation() || ready.pendingTransaction != null) return@launchOperation
            if (!checkOnline()) return@launchOperation
            refreshMetadataLocked(journalId = null, scheduled = true)
        }
    }

    private suspend fun refreshMetadataLocked(journalId: Long?, scheduled: Boolean) {
        if (_state.value !is ZephyrUiState.Ready) return
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
            completeJournalEntry(journalId, outcome.success, outcome.message)
            val metadataStatus = if (outcome.success) CandidateMetadataStatus.Refreshed else CandidateMetadataStatus.Failed(outcome.message)
            _state.updateReady {
                it.copy(
                    sdkmanStatus = it.sdkmanStatus.copy(metadataStatus = metadataStatus),
                    catalog = catalog,
                    catalogCachedAtEpochMillis = null,
                    catalogIsCached = false,
                    isCatalogLoading = false,
                    lastOutcome = if (scheduled) {
                        "Scheduled metadata refresh completed. Loaded ${catalog.size} packages."
                    } else {
                        "${outcome.message} Loaded ${catalog.size} packages."
                    },
                    errorMessage = if (outcome.success) null else outcome.message,
                )
            }
        }.onFailure {
            ZephyrLogger.warn("Candidate metadata refresh failed.", it)
            completeJournalEntry(journalId, false, it.message ?: "Metadata refresh failed.")
            _state.updateReady { state ->
                state.copy(
                    sdkmanStatus = state.sdkmanStatus.copy(metadataStatus = CandidateMetadataStatus.Failed(it.message.orEmpty())),
                    isCatalogLoading = false,
                    errorMessage = "Candidate metadata refresh failed: ${it.message}",
                )
            }
        }
    }

    fun checkSdkmanUpdates(journalId: Long? = null) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            runCatchingCancellable {
                val result = repository.selfUpdate()
                result to repository.cliVersion()
            }.onSuccess { (result, version) ->
                if (result is SdkmanSelfUpdateStatus.Failed) ZephyrLogger.warn("SDKMAN self-update failed: ${result.message}")
                completeJournalEntry(
                    journalId,
                    result !is SdkmanSelfUpdateStatus.Failed,
                    result.outcomeMessage() ?: "SDKMAN update check completed.",
                )
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
                completeJournalEntry(journalId, false, it.message ?: "SDKMAN self-update failed.")
                fail("SDKMAN self-update failed: ${it.message}")
            }
        }
    }

    fun scanLocalOnly() {
        launchOperation {
            if (_state.value !is ZephyrUiState.Ready) return@launchOperation
            _state.updateReady { it.copy(localOnlyScanInProgress = true, errorMessage = null) }
            if (!checkOnline()) {
                fail("Local-only scanning requires the SDKMAN service, but Zephyr is offline.")
                return@launchOperation
            }
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

    fun install(candidate: String, version: String, journalId: Long? = null) = mutate(journalId) {
        repository.install(candidate, version)
    }

    private fun batchInstall(transaction: SdkmanTransaction.BatchInstall, journalId: Long) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            var progress = transaction.targets.map { BatchInstallProgress(it) }
            _state.updateReady { it.copy(batchInstallProgress = progress) }
            transaction.targets.forEachIndexed { index, target ->
                progress = progress.updateBatchItem(index, BatchItemStatus.Running)
                _state.updateReady { it.copy(batchInstallProgress = progress) }
                val outcome = runCatchingCancellable {
                    repository.install(target.candidate, target.version)
                }.getOrElse { failure ->
                    CommandOutcome(false, failure.message ?: "Install failed.")
                }
                progress = progress.updateBatchItem(
                    index = index,
                    status = if (outcome.success) BatchItemStatus.Succeeded else BatchItemStatus.Failed,
                    outcome = outcome.message,
                )
                _state.updateReady { it.copy(batchInstallProgress = progress) }
            }
            val candidates = runCatchingCancellable { repository.installedCandidates() }
                .getOrElse { (_state.value as? ZephyrUiState.Ready)?.candidates.orEmpty() }
            val succeeded = progress.count { it.status == BatchItemStatus.Succeeded }
            val summary = "$succeeded of ${progress.size} selected installs succeeded."
            val allSucceeded = succeeded == progress.size
            completeJournalEntry(journalId, allSucceeded, summary)
            _state.updateReady {
                it.copy(
                    candidates = candidates,
                    catalog = it.catalog.withInstalledCandidates(candidates),
                    batchInstallProgress = progress,
                    isRefreshing = false,
                    lastOutcome = summary,
                    errorMessage = if (allSucceeded) null else summary,
                )
            }
        }
    }

    private fun batchUninstall(transaction: SdkmanTransaction.BatchUninstall, journalId: Long) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            var progress = transaction.targets.map { BatchUninstallProgress(it) }
            _state.updateReady { it.copy(batchUninstallProgress = progress) }
            transaction.targets.forEachIndexed { index, target ->
                progress = progress.updateBatchUninstallItem(index, BatchItemStatus.Running)
                _state.updateReady { it.copy(batchUninstallProgress = progress) }
                val outcome = runCatchingCancellable {
                    repository.uninstall(target.candidate, target.version)
                }.getOrElse { failure ->
                    CommandOutcome(false, failure.message ?: "Uninstall failed.")
                }
                progress = progress.updateBatchUninstallItem(
                    index = index,
                    status = if (outcome.success) BatchItemStatus.Succeeded else BatchItemStatus.Failed,
                    outcome = outcome.message,
                )
                _state.updateReady { it.copy(batchUninstallProgress = progress) }
            }
            val candidates = runCatchingCancellable { repository.installedCandidates() }
                .getOrElse { (_state.value as? ZephyrUiState.Ready)?.candidates.orEmpty() }
            val succeeded = progress.count { it.status == BatchItemStatus.Succeeded }
            val summary = "$succeeded of ${progress.size} selected uninstalls succeeded."
            val allSucceeded = succeeded == progress.size
            completeJournalEntry(journalId, allSucceeded, summary)
            _state.updateReady {
                it.copy(
                    candidates = candidates,
                    catalog = it.catalog.withInstalledCandidates(candidates),
                    batchUninstallProgress = progress,
                    isRefreshing = false,
                    lastOutcome = summary,
                    errorMessage = if (allSucceeded) null else summary,
                )
            }
        }
    }

    private fun restoreSnapshot(transaction: SdkmanTransaction.SnapshotRestore, journalId: Long) {
        launchOperation {
            if (!beginRefresh()) return@launchOperation
            var progress = transaction.commands.map { SnapshotRestoreProgress(it) }
            _state.updateReady { it.copy(snapshotRestoreProgress = progress) }
            transaction.commands.forEachIndexed { index, command ->
                progress = progress.updateSnapshotRestoreItem(index, BatchItemStatus.Running)
                _state.updateReady { it.copy(snapshotRestoreProgress = progress) }
                val outcome = runCatchingCancellable {
                    when (command.action) {
                        SdkmanCommandAction.Install ->
                            repository.install(requireNotNull(command.candidate), requireNotNull(command.version))
                        SdkmanCommandAction.SetDefault ->
                            repository.setDefault(requireNotNull(command.candidate), requireNotNull(command.version))
                        else -> error("Unsupported snapshot restore action.")
                    }
                }.getOrElse { failure ->
                    CommandOutcome(false, failure.message ?: "Snapshot restore step failed.")
                }
                progress = progress.updateSnapshotRestoreItem(
                    index = index,
                    status = if (outcome.success) BatchItemStatus.Succeeded else BatchItemStatus.Failed,
                    outcome = outcome.message,
                )
                _state.updateReady { it.copy(snapshotRestoreProgress = progress) }
            }
            val candidates = runCatchingCancellable { repository.installedCandidates() }
                .getOrElse { (_state.value as? ZephyrUiState.Ready)?.candidates.orEmpty() }
            val succeeded = progress.count { it.status == BatchItemStatus.Succeeded }
            val summary = "$succeeded of ${progress.size} snapshot restore steps succeeded."
            val allSucceeded = succeeded == progress.size
            completeJournalEntry(journalId, allSucceeded, summary)
            _state.updateReady {
                it.copy(
                    candidates = candidates,
                    catalog = it.catalog.withInstalledCandidates(candidates),
                    snapshotRestoreProgress = progress,
                    isRefreshing = false,
                    lastOutcome = summary,
                    errorMessage = if (allSucceeded) null else "$summary Review and resume the remaining plan.",
                )
            }
        }
    }

    fun uninstall(candidate: String, version: String, journalId: Long? = null) = mutate(journalId) {
        repository.uninstall(candidate, version)
    }

    fun setDefault(candidate: String, version: String, journalId: Long? = null) = mutate(journalId) {
        repository.setDefault(candidate, version)
    }

    fun cleanLocalOnly(candidate: String, versions: List<String>, journalId: Long? = null) = mutate(journalId) {
        repository.cleanLocalOnly(candidate, versions)
    }

    private fun ensureCatalog() {
        val ready = _state.value as? ZephyrUiState.Ready ?: return
        if (ready.catalog.isNotEmpty() && !ready.catalogIsCached) return
        launchQueuedOperation {
            val current = _state.value as? ZephyrUiState.Ready ?: return@launchQueuedOperation
            if (current.catalog.isNotEmpty() && !current.catalogIsCached) return@launchQueuedOperation
            _state.updateReady { it.copy(isCatalogLoading = true) }
            if (!checkOnline()) {
                if (current.catalogIsCached && current.catalog.isNotEmpty()) {
                    _state.updateReady {
                        it.copy(
                            isCatalogLoading = false,
                            errorMessage = null,
                            lastOutcome = "Showing cached candidate metadata while offline.",
                        )
                    }
                } else {
                    fail("Catalog loading requires the SDKMAN service, but Zephyr is offline.")
                }
                return@launchQueuedOperation
            }
            runCatchingCancellable {
                val catalog = retryRead(RetryableReadOperation.CandidateCatalog) {
                    repository.catalog(refreshMetadata = true)
                }
                _state.updateReady {
                    it.copy(
                        catalog = catalog,
                        catalogCachedAtEpochMillis = null,
                        catalogIsCached = false,
                        isCatalogLoading = false,
                        lastOutcome = "Loaded ${catalog.size} SDKMAN packages.",
                    )
                }
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
            if (!checkOnline()) {
                fail("Version loading requires the SDKMAN service, but Zephyr is offline.")
                return@launchQueuedOperation
            }
            runCatchingCancellable {
                val merged = retryRead(RetryableReadOperation.CandidateDetail) {
                    repository.mergedCandidate(candidate)
                }
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

    private fun mutate(journalId: Long?, block: suspend () -> CommandOutcome) {
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
                completeJournalEntry(journalId, result.outcome.success, result.outcome.message)
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
                completeJournalEntry(journalId, false, it.message ?: "SDKMAN mutation failed.")
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

    private fun startJournalEntry(transaction: SdkmanTransaction): Long {
        val id = nextJournalId++
        val entry = OperationJournalEntry(
            id = id,
            transaction = transaction,
            startedAtEpochMillis = clock(),
        )
        _state.updateReady { it.copy(operationJournal = listOf(entry) + it.operationJournal) }
        return id
    }

    private fun completeJournalEntry(journalId: Long?, success: Boolean, outcome: String) {
        if (journalId == null) return
        _state.updateReady { ready ->
            ready.copy(
                operationJournal = ready.operationJournal.map { entry ->
                    if (entry.id == journalId) {
                        entry.copy(
                            completedAtEpochMillis = clock(),
                            status = if (success) OperationStatus.Succeeded else OperationStatus.Failed,
                            outcome = outcome,
                        )
                    } else {
                        entry
                    }
                },
            )
        }
    }

    private suspend fun loadProtectedVersions(): Set<ProtectedVersion> =
        runCatchingCancellable {
            repository.protectedVersions()
        }.getOrElse { failure ->
            ZephyrLogger.warn("Unable to load protected SDKMAN versions.", failure)
            emptySet()
        }

    private suspend fun loadIntegrityChecks(): List<IntegrityCheck> =
        runCatchingCancellable {
            repository.integrityChecks()
        }.getOrElse { failure ->
            ZephyrLogger.warn("Unable to run SDKMAN integrity checks.", failure)
            emptyList()
        }

    private suspend fun <T> retryRead(
        operation: RetryableReadOperation,
        block: suspend () -> T,
    ): T {
        val maximumAttempts = readRetryDelaysMillis.size + 1
        var lastFailure: Exception? = null
        repeat(maximumAttempts) { index ->
            try {
                val result = block()
                _state.updateReady { it.copy(readRetryStatus = null) }
                return result
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                lastFailure = exception
                val nextAttempt = index + 2
                if (nextAttempt <= maximumAttempts) {
                    _state.updateReady {
                        it.copy(
                            readRetryStatus = ReadRetryStatus(operation, nextAttempt, maximumAttempts),
                        )
                    }
                    delay(readRetryDelaysMillis[index])
                }
            }
        }
        _state.updateReady { it.copy(readRetryStatus = null) }
        throw requireNotNull(lastFailure)
    }

    private suspend fun checkOnline(): Boolean {
        val status = runCatchingCancellable {
            repository.checkConnectivity()
        }.getOrElse { failure ->
            ZephyrLogger.warn("SDKMAN connectivity check failed.", failure)
            ConnectivityStatus(
                state = ConnectivityState.Offline,
                checkedAtEpochMillis = clock(),
                detail = "Connectivity could not be verified.",
            )
        }
        _state.updateReady { it.copy(connectivityStatus = status) }
        return status.state == ConnectivityState.Online
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

private fun List<BatchInstallProgress>.updateBatchItem(
    index: Int,
    status: BatchItemStatus,
    outcome: String? = null,
): List<BatchInstallProgress> =
    mapIndexed { itemIndex, item ->
        if (itemIndex == index) item.copy(status = status, outcome = outcome) else item
    }

private fun List<BatchUninstallProgress>.updateBatchUninstallItem(
    index: Int,
    status: BatchItemStatus,
    outcome: String? = null,
): List<BatchUninstallProgress> =
    mapIndexed { itemIndex, item ->
        if (itemIndex == index) item.copy(status = status, outcome = outcome) else item
    }

private fun List<SnapshotRestoreProgress>.updateSnapshotRestoreItem(
    index: Int,
    status: BatchItemStatus,
    outcome: String? = null,
): List<SnapshotRestoreProgress> =
    mapIndexed { itemIndex, item ->
        if (itemIndex == index) item.copy(status = status, outcome = outcome) else item
    }

private fun ZephyrUiState.Ready.displaysCandidate(candidate: String): Boolean =
    when (val currentRoute = route) {
        is ZephyrRoute.JdkDetail -> currentRoute.candidate == candidate
        is ZephyrRoute.SdkDetail -> currentRoute.candidate == candidate
        ZephyrRoute.BrowseJdks -> candidate == "java"
        else -> false
    }

private fun ZephyrUiState.Ready.hasActiveOperation(): Boolean =
    isRefreshing ||
        isCatalogLoading ||
        localOnlyScanInProgress ||
        detailLoadingCandidate != null ||
        journalExportInProgress ||
        diagnosticsExportInProgress ||
        transactionPreviewLoading
