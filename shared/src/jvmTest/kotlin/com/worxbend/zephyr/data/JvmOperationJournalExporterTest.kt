package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.SdkmanTransaction
import java.nio.file.Files
import kotlin.io.path.readText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmOperationJournalExporterTest {
    @Test
    fun exportsSearchableSessionHistoryAsEscapedCsv() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-journal-test-")
        try {
            val exporter = JvmOperationJournalExporter(
                outputDirectory = { directory },
                clock = { 1_721_234_567_000L },
                sensitivePaths = { listOf(System.getProperty("user.home")) },
            )
            val entries = listOf(
                OperationJournalEntry(
                    id = 1,
                    transaction = SdkmanTransaction.Install("gradle", "9.0.0"),
                    startedAtEpochMillis = 1_721_234_560_000L,
                    completedAtEpochMillis = 1_721_234_561_000L,
                    status = OperationStatus.Succeeded,
                    outcome = "Installed, with metadata at ${System.getProperty("user.home")}/.sdkman",
                ),
            )

            val result = exporter.export(entries)
            val destination = java.nio.file.Path.of(result.path)
            val csv = destination.readText()

            assertEquals(1, result.exportedEntries)
            assertTrue(Files.isRegularFile(destination))
            assertTrue(csv.startsWith("started,completed,status,operation,commands,outcome"))
            assertTrue(csv.contains("\"Succeeded\""))
            assertTrue(csv.contains("\"Install gradle 9.0.0\""))
            assertTrue(csv.contains("\"Installed, with metadata at <redacted-path>/.sdkman\""))
            assertFalse(csv.contains(System.getProperty("user.home")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
