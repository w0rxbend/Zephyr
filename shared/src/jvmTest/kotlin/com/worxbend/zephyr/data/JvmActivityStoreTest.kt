package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.ActivityAction
import com.worxbend.zephyr.domain.ActivityEvent
import com.worxbend.zephyr.domain.ActivitySeverity
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JvmActivityStoreTest {
    @Test
    fun roundTripsAcknowledgementSeverityAndAllowlistedAction() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-activity-store-")
        try {
            val destination = directory.resolve("activity.ledger")
            val events = listOf(
                ActivityEvent(
                    id = 7,
                    timestampEpochMillis = 123,
                    severity = ActivitySeverity.Warning,
                    message = "Review the interrupted task.",
                    action = ActivityAction.OpenTaskCenter,
                    acknowledged = true,
                ),
            )
            val store = JvmActivityStore(destination)

            store.save(events)

            assertEquals(events, store.load())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun redactsPathsAndBoundsDurableRetention() = runBlocking {
        val directory = Files.createTempDirectory("zephyr-activity-store-")
        try {
            val destination = directory.resolve("activity.ledger")
            val secretHome = "/private/example/sdkman"
            val events = (1L..4L).map { id ->
                ActivityEvent(
                    id = id,
                    timestampEpochMillis = id,
                    severity = ActivitySeverity.Error,
                    message = "Failure under $secretHome/candidates/java",
                )
            }
            val store = JvmActivityStore(
                destination = destination,
                sensitivePaths = { listOf(secretHome) },
                retentionLimit = 2,
            )

            store.save(events)

            val content = Files.readString(destination)
            assertFalse(content.contains(secretHome))
            assertEquals(listOf(4L, 3L), store.load().map(ActivityEvent::id))
            assertTrue(store.load().all { "<redacted-path>" in it.message })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownActivityLedgerVersions() {
        val failure = runCatching {
            parseActivityLedger("ZEPHYR_ACTIVITY_CENTER\t99\n")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
