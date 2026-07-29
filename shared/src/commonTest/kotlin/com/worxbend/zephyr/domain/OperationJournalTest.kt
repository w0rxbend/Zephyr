package com.worxbend.zephyr.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OperationJournalTest {
    private val entries = listOf(
        OperationJournalEntry(
            id = 2,
            transaction = SdkmanTransaction.Uninstall("java", "17.0.1-tem"),
            startedAtEpochMillis = 200,
            completedAtEpochMillis = 210,
            status = OperationStatus.Failed,
            outcome = "Network unavailable",
        ),
        OperationJournalEntry(
            id = 1,
            transaction = SdkmanTransaction.Install("gradle", "9.0.0"),
            startedAtEpochMillis = 100,
            completedAtEpochMillis = 110,
            status = OperationStatus.Succeeded,
            outcome = "Installed",
        ),
    )

    @Test
    fun searchesActionsCandidatesVersionsStatusesAndOutcomes() {
        assertEquals(listOf(2L), entries.searchOperationJournal("uninstall").map { it.id })
        assertEquals(listOf(1L), entries.searchOperationJournal("gradle").map { it.id })
        assertEquals(listOf(2L), entries.searchOperationJournal("17.0.1").map { it.id })
        assertEquals(listOf(1L), entries.searchOperationJournal("succeeded").map { it.id })
        assertEquals(listOf(2L), entries.searchOperationJournal("network").map { it.id })
        assertEquals(entries, entries.searchOperationJournal(""))
    }
}
