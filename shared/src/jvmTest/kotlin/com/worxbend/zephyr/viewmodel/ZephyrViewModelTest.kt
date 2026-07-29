package com.worxbend.zephyr.viewmodel

import com.worxbend.zephyr.data.OperationJournalExporter
import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.DiskImpactEstimate
import com.worxbend.zephyr.domain.DiskImpactKind
import com.worxbend.zephyr.domain.EstimateConfidence
import com.worxbend.zephyr.domain.JournalExportResult
import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.SdkmanTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ZephyrViewModelTest {
    @Test
    fun initialLoadDoesNotRefreshRemoteMetadata() {
        val repository = FakeSdkmanRepository()
        val viewModel = ZephyrViewModel(repository, testScope())

        assertEquals(0, repository.catalogCalls)
        assertEquals(0, repository.metadataRefreshCalls)
        viewModel.close()
    }

    @Test
    fun openingBrowseLoadsTheCatalogWithMetadataRefresh() {
        val repository = FakeSdkmanRepository()
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.navigate(ZephyrRoute.BrowseSdks)

        assertEquals(listOf(true), repository.catalogRefreshRequests)
        viewModel.close()
    }

    @Test
    fun metadataFailureLeavesTheUiInteractiveAndReportsTheError() {
        val repository = FakeSdkmanRepository(catalogFailure = IllegalStateException("network unavailable"))
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.refreshMetadata()

        val state = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertFalse(state.isCatalogLoading)
        assertEquals("Candidate metadata refresh failed: network unavailable", state.errorMessage)
    }

    @Test
    fun selfUpdateExceptionLeavesTheUiInteractiveAndReportsTheError() {
        val repository = FakeSdkmanRepository(selfUpdateFailure = IllegalStateException("connection reset"))
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.checkSdkmanUpdates()

        val state = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertFalse(state.isRefreshing)
        assertEquals("SDKMAN self-update failed: connection reset", state.errorMessage)
    }

    @Test
    fun refreshPreservesTheCurrentRouteInsteadOfWritingAStaleSnapshot() {
        val repository = FakeSdkmanRepository()
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.navigate(ZephyrRoute.BrowseSdks)
        viewModel.refreshInstalled()

        val state = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertIs<ZephyrRoute.BrowseSdks>(state.route)
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun openingAnUninstalledPackageDoesNotAddItToInstalledCandidates() {
        val repository = FakeSdkmanRepository(remoteDetail = remoteCandidate("gradle"))
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.navigate(ZephyrRoute.SdkDetail("gradle"))

        val state = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertEquals("gradle", state.selectedCandidate?.name)
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun ignoresRepeatedOperationsWhileAnotherOperationIsRunning() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        val repository = FakeSdkmanRepository(refreshStarted = refreshStarted, refreshGate = refreshGate)
        val viewModel = ZephyrViewModel(repository, Dispatchers.Default)
        withTimeout(1_000) { viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first() }

        viewModel.refreshInstalled()
        withTimeout(1_000) { refreshStarted.await() }
        viewModel.refreshInstalled()
        refreshGate.complete(Unit)
        withTimeout(1_000) {
            viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first { !it.isRefreshing }
        }

        assertEquals(2, repository.installedCandidatesCalls)
        viewModel.close()
    }

    @Test
    fun loadsARequestedDetailAfterAnActiveScanFinishes() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        val java = remoteCandidate("java", CandidateKind.Jdk)
        val repository = FakeSdkmanRepository(
            remoteDetail = java,
            installedCandidate = java,
            refreshStarted = refreshStarted,
            refreshGate = refreshGate,
        )
        val viewModel = ZephyrViewModel(repository, Dispatchers.Default)
        withTimeout(1_000) { viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first() }

        viewModel.scanLocalOnly()
        withTimeout(1_000) { refreshStarted.await() }
        viewModel.navigate(ZephyrRoute.JdkDetail())
        refreshGate.complete(Unit)

        val state = withTimeout(1_000) {
            viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first {
                it.route is ZephyrRoute.JdkDetail && it.selectedCandidate?.name == "java"
            }
        }

        assertEquals("java", state.selectedCandidate?.name)
        viewModel.close()
    }

    @Test
    fun loadsBrowseCatalogRequestedDuringAnActiveScan() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        val repository = FakeSdkmanRepository(refreshStarted = refreshStarted, refreshGate = refreshGate)
        val viewModel = ZephyrViewModel(repository, Dispatchers.Default)
        withTimeout(1_000) { viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first() }

        viewModel.scanLocalOnly()
        withTimeout(1_000) { refreshStarted.await() }
        viewModel.navigate(ZephyrRoute.BrowseSdks)
        refreshGate.complete(Unit)

        withTimeout(1_000) {
            viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first {
                it.route is ZephyrRoute.BrowseSdks && repository.catalogRefreshRequests == listOf(true)
            }
        }

        viewModel.close()
    }

    @Test
    fun leavingASlowDetailRequestClearsOnlyTheDetailLoadingState() = runBlocking {
        val detailStarted = CompletableDeferred<Unit>()
        val detailGate = CompletableDeferred<Unit>()
        val detailCompleted = CompletableDeferred<Unit>()
        val repository = FakeSdkmanRepository(
            remoteDetail = remoteCandidate("gradle"),
            detailStarted = detailStarted,
            detailGate = detailGate,
            detailCompleted = detailCompleted,
        )
        val viewModel = ZephyrViewModel(repository, Dispatchers.Default)
        withTimeout(1_000) { viewModel.state.filterIsInstance<ZephyrUiState.Ready>().first() }

        viewModel.navigate(ZephyrRoute.SdkDetail("gradle"))
        withTimeout(1_000) { detailStarted.await() }
        viewModel.navigate(ZephyrRoute.InstalledSdks)
        detailGate.complete(Unit)
        withTimeout(1_000) { detailCompleted.await() }

        val state = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertIs<ZephyrRoute.InstalledSdks>(state.route)
        assertEquals(null, state.detailLoadingCandidate)
        assertFalse(state.isRefreshing)
        viewModel.close()
    }

    @Test
    fun mutationRunsOnlyAfterItsTypedTransactionIsConfirmed() {
        val repository = FakeSdkmanRepository()
        val viewModel = ZephyrViewModel(repository, testScope())
        val transaction = SdkmanTransaction.Install("java", "21.0.5-tem")

        viewModel.requestTransaction(transaction)

        val pending = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertEquals(transaction, pending.pendingTransaction)
        assertEquals(DiskImpactKind.None, pending.pendingTransactionDiskImpact?.kind)
        assertTrue(repository.mutationCalls.isEmpty())

        viewModel.confirmTransaction()

        assertEquals(listOf("install:java:21.0.5-tem"), repository.mutationCalls)
        val ready = assertIs<ZephyrUiState.Ready>(viewModel.state.value)
        assertEquals(null, ready.pendingTransaction)
        assertEquals(OperationStatus.Succeeded, ready.operationJournal.single().status)
        assertEquals("Installed", ready.operationJournal.single().outcome)
        viewModel.close()
    }

    @Test
    fun exportsTheCompletedSessionJournal() {
        val repository = FakeSdkmanRepository()
        val exporter = FakeOperationJournalExporter()
        var now = 1_000L
        val viewModel = ZephyrViewModel(repository, testScope(), exporter) { now++ }

        viewModel.requestTransaction(SdkmanTransaction.SetDefault("java", "21.0.5-tem"))
        viewModel.confirmTransaction()
        viewModel.exportJournal()

        val exported = exporter.exported.single()
        assertEquals(OperationStatus.Succeeded, exported.single().status)
        assertEquals(1_000L, exported.single().startedAtEpochMillis)
        assertEquals(1_001L, exported.single().completedAtEpochMillis)
        assertEquals(
            "Exported 1 journal entries to /tmp/zephyr-journal.csv.",
            assertIs<ZephyrUiState.Ready>(viewModel.state.value).lastOutcome,
        )
        viewModel.close()
    }

    @Test
    fun failedMutationIsRetainedInTheJournal() {
        val repository = FakeSdkmanRepository(
            installOutcome = CommandOutcome(false, "Download unavailable"),
        )
        val viewModel = ZephyrViewModel(repository, testScope())

        viewModel.requestTransaction(SdkmanTransaction.Install("java", "21.0.5-tem"))
        viewModel.confirmTransaction()

        val entry = assertIs<ZephyrUiState.Ready>(viewModel.state.value).operationJournal.single()
        assertEquals(OperationStatus.Failed, entry.status)
        assertEquals("Download unavailable", entry.outcome)
        viewModel.close()
    }

    @Test
    fun cleanupRetryIncludesOnlyVersionsStillVerifiedAsLocalOnly() {
        val installed = remoteCandidate("java", CandidateKind.Jdk).copy(
            installedVersions = listOf(
                CandidateVersion("17.0.1-tem", true, false, false),
            ),
            hasLocalOnlyVersions = true,
            localOnlyVersionCount = 1,
            localOnlyVersions = listOf("17.0.1-tem"),
        )
        val viewModel = ZephyrViewModel(
            FakeSdkmanRepository(installedCandidate = installed),
            testScope(),
        )

        viewModel.retryTransaction(
            SdkmanTransaction.CleanLocalOnly(
                "java",
                listOf("17.0.1-tem", "19.0.2-tem"),
            ),
        )

        val retry = assertIs<SdkmanTransaction.CleanLocalOnly>(
            assertIs<ZephyrUiState.Ready>(viewModel.state.value).pendingTransaction,
        )
        assertEquals(listOf("17.0.1-tem"), retry.versions)
        viewModel.close()
    }

    @Test
    fun protectionChangesAreReflectedInReadyState() {
        val viewModel = ZephyrViewModel(FakeSdkmanRepository(), testScope())
        val protected = ProtectedVersion("java", "21.0.5-tem")

        viewModel.setVersionProtected(protected.candidate, protected.version, true)

        assertTrue(protected in assertIs<ZephyrUiState.Ready>(viewModel.state.value).protectedVersions)

        viewModel.setVersionProtected(protected.candidate, protected.version, false)

        assertFalse(protected in assertIs<ZephyrUiState.Ready>(viewModel.state.value).protectedVersions)
        viewModel.close()
    }

    private fun testScope() = Dispatchers.Unconfined
}

private fun remoteCandidate(name: String, kind: CandidateKind = CandidateKind.Sdk) = Candidate(
    name = name,
    displayName = name,
    description = null,
    websiteUrl = null,
    kind = kind,
    installedVersions = emptyList(),
    defaultVersion = null,
    hasLocalOnlyVersions = false,
    localOnlyVersionCount = 0,
    localOnlyVersions = emptyList(),
)

private class FakeSdkmanRepository(
    private val catalogFailure: Throwable? = null,
    private val selfUpdateFailure: Throwable? = null,
    private val remoteDetail: Candidate? = null,
    private val installedCandidate: Candidate? = null,
    private val refreshStarted: CompletableDeferred<Unit>? = null,
    private val refreshGate: CompletableDeferred<Unit>? = null,
    private val detailStarted: CompletableDeferred<Unit>? = null,
    private val detailGate: CompletableDeferred<Unit>? = null,
    private val detailCompleted: CompletableDeferred<Unit>? = null,
    private val installOutcome: CommandOutcome = CommandOutcome(true, "Installed"),
) : SdkmanRepository {
    var installedCandidatesCalls: Int = 0
        private set
    var catalogCalls: Int = 0
        private set
    val catalogRefreshRequests = mutableListOf<Boolean>()
    var metadataRefreshCalls: Int = 0
        private set
    val mutationCalls = mutableListOf<String>()
    private val protected = mutableSetOf<ProtectedVersion>()

    override suspend fun detect(): SdkmanStatus = SdkmanStatus(isInstalled = true, home = "/tmp/sdkman")

    override suspend fun cliVersion(): String = "SDKMAN 5"

    override suspend fun installedCandidates(): List<Candidate> {
        installedCandidatesCalls += 1
        if (installedCandidatesCalls > 1) {
            refreshStarted?.complete(Unit)
            refreshGate?.await()
        }
        return listOfNotNull(installedCandidate)
    }

    override suspend fun catalog(refreshMetadata: Boolean): List<CandidateCatalogItem> {
        catalogCalls += 1
        catalogRefreshRequests += refreshMetadata
        catalogFailure?.let { throw it }
        return emptyList()
    }

    override suspend fun versions(candidate: String): List<CandidateVersion> = emptyList()

    override suspend fun mergedCandidate(candidate: String): Candidate? {
        detailStarted?.complete(Unit)
        try {
            detailGate?.await()
        } finally {
            detailCompleted?.complete(Unit)
        }
        return remoteDetail?.takeIf { it.name == candidate }
    }

    override suspend fun estimateDiskImpact(transaction: SdkmanTransaction): DiskImpactEstimate =
        DiskImpactEstimate(
            kind = DiskImpactKind.None,
            bytes = 0,
            confidence = EstimateConfidence.Exact,
            explanation = "No test disk impact.",
        )

    override suspend fun protectedVersions(): Set<ProtectedVersion> = protected

    override suspend fun setVersionProtected(
        candidate: String,
        version: String,
        protected: Boolean,
    ): CommandOutcome {
        val target = ProtectedVersion(candidate, version)
        if (protected) this.protected += target else this.protected -= target
        return CommandOutcome(true, if (protected) "Protected" else "Unprotected")
    }

    override suspend fun refreshCandidateMetadata(): CommandOutcome {
        metadataRefreshCalls += 1
        return CommandOutcome(true, "Metadata refreshed")
    }

    override suspend fun selfUpdate(): SdkmanSelfUpdateStatus {
        selfUpdateFailure?.let { throw it }
        return SdkmanSelfUpdateStatus.UpToDate
    }

    override suspend fun install(candidate: String, version: String): CommandOutcome {
        mutationCalls += "install:$candidate:$version"
        return installOutcome
    }

    override suspend fun uninstall(candidate: String, version: String): CommandOutcome {
        mutationCalls += "uninstall:$candidate:$version"
        return CommandOutcome(true, "Uninstalled")
    }

    override suspend fun setDefault(candidate: String, version: String): CommandOutcome {
        mutationCalls += "default:$candidate:$version"
        return CommandOutcome(true, "Default")
    }

    override suspend fun cleanLocalOnly(candidate: String, versions: List<String>): CommandOutcome {
        mutationCalls += "clean:$candidate:${versions.joinToString(",")}"
        return CommandOutcome(true, "Cleaned")
    }
}

private class FakeOperationJournalExporter : OperationJournalExporter {
    val exported = mutableListOf<List<OperationJournalEntry>>()

    override suspend fun export(entries: List<OperationJournalEntry>): JournalExportResult {
        exported += entries
        return JournalExportResult("/tmp/zephyr-journal.csv", entries.size)
    }
}
