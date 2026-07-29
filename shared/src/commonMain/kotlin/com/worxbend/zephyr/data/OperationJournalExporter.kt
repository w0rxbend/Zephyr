package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.JournalExportResult
import com.worxbend.zephyr.domain.OperationJournalEntry

interface OperationJournalExporter {
    suspend fun export(entries: List<OperationJournalEntry>): JournalExportResult
}

expect fun createOperationJournalExporter(): OperationJournalExporter

expect fun currentEpochMillis(): Long

expect fun formatLocalTimestamp(epochMillis: Long): String
