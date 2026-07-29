package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.DiagnosticsSnapshot
import com.worxbend.zephyr.domain.SupportBundleExportResult

interface DiagnosticsExporter {
    suspend fun export(snapshot: DiagnosticsSnapshot): SupportBundleExportResult
}

expect fun createDiagnosticsExporter(): DiagnosticsExporter
