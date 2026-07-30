package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.InstallTarget
import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.OperationStep
import com.worxbend.zephyr.domain.OperationStepStatus
import com.worxbend.zephyr.domain.PlannedSdkmanCommand
import com.worxbend.zephyr.domain.SdkmanCommandAction
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.UpdateActivationTarget
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JvmOperationStoreTest {
    @Test
    fun roundTripsTypedStableUpdateActivation() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-update-task-store-")
        try {
            val destination = directory.resolve("tasks.ledger")
            val transaction = SdkmanTransaction.UpdateActivation(
                listOf(
                    UpdateActivationTarget("gradle", "8.14", true),
                    UpdateActivationTarget("kotlin", "2.2.0", false),
                ),
            )
            val entry = OperationJournalEntry(52, transaction, 400)

            JvmOperationStore(destination).also { store ->
                store.save(listOf(entry))
                assertEquals(entry, store.load().single())
            }
            Unit
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun roundTripsProfileActivationIdentityAndTypedPlan() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-profile-task-store-")
        try {
            val destination = directory.resolve("tasks.ledger")
            val transaction = SdkmanTransaction.ToolchainActivation(
                profileName = "Backend & APIs",
                commands = listOf(
                    PlannedSdkmanCommand(SdkmanCommandAction.Install, "java", "21-tem"),
                    PlannedSdkmanCommand(SdkmanCommandAction.SetDefault, "java", "21-tem"),
                ),
            )
            val entry = OperationJournalEntry(
                id = 51,
                transaction = transaction,
                startedAtEpochMillis = 300,
            )

            JvmOperationStore(destination).also { store ->
                store.save(listOf(entry))
                assertEquals(entry, store.load().single())
            }
            Unit
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun roundTripsTypedStepsAndStatuses() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-task-store-")
        try {
            val destination = directory.resolve("tasks.ledger")
            val transaction = SdkmanTransaction.BatchInstall(
                listOf(
                    InstallTarget("java", "21.0.5-tem"),
                    InstallTarget("gradle", "9.1.0"),
                ),
            )
            val entry = OperationJournalEntry(
                id = 41,
                transaction = transaction,
                startedAtEpochMillis = 100,
                completedAtEpochMillis = 200,
                status = OperationStatus.Interrupted,
                outcome = "Restarted before completion.",
                steps = transaction.commands.mapIndexed { index, command ->
                    OperationStep(
                        index = index,
                        command = command,
                        status = if (index == 0) OperationStepStatus.Succeeded else OperationStepStatus.Interrupted,
                        completedAtEpochMillis = 150L.takeIf { index == 0 },
                    )
                },
            )
            val store = JvmOperationStore(destination)

            store.save(listOf(entry))
            val restored = store.load().single()

            assertEquals(entry, restored)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun redactsPathsAndBoundsRetention() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-task-store-")
        try {
            val destination = directory.resolve("tasks.ledger")
            val secretHome = "/private/example/sdkman"
            val entries = (1L..4L).map { id ->
                OperationJournalEntry(
                    id = id,
                    transaction = SdkmanTransaction.Install("java", "21.0.$id-tem"),
                    startedAtEpochMillis = id,
                    status = OperationStatus.Failed,
                    outcome = "Failure under $secretHome/candidates/java",
                )
            }
            val store = JvmOperationStore(
                destination = destination,
                sensitivePaths = { listOf(secretHome) },
                retentionLimit = 2,
            )

            store.save(entries)

            val content = Files.readString(destination)
            assertFalse(content.contains(secretHome))
            assertFalse(content.contains("/candidates/java"))
            assertEquals(listOf(4L, 3L), store.load().map(OperationJournalEntry::id))
            assertTrue(store.load().all { "<redacted-path>" in it.outcome.orEmpty() })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownLedgerVersions() {
        val failure = runCatching { parseOperationLedger("ZEPHYR_TASK_CENTER\t99\n") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
