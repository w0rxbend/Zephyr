package com.worxbend.zephyr.domain

data class DiagnosticsSnapshot(
    val generatedAtEpochMillis: Long,
    val sdkmanStatus: SdkmanStatus,
    val connectivityStatus: ConnectivityStatus,
    val integrityChecks: List<IntegrityCheck>,
    val installedCandidates: Int,
    val installedVersions: Int,
    val localOnlyVersions: Int,
    val protectedVersions: Int,
    val journal: List<OperationJournalEntry>,
)

data class SupportBundleExportResult(
    val path: String,
)
