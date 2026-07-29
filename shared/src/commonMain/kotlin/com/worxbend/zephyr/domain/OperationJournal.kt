package com.worxbend.zephyr.domain

enum class OperationStatus(val label: String) {
    Running("Running"),
    Succeeded("Succeeded"),
    Failed("Failed"),
}

data class OperationJournalEntry(
    val id: Long,
    val transaction: SdkmanTransaction,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val status: OperationStatus = OperationStatus.Running,
    val outcome: String? = null,
)

data class JournalExportResult(
    val path: String,
    val exportedEntries: Int,
)

fun List<OperationJournalEntry>.searchOperationJournal(query: String): List<OperationJournalEntry> {
    if (query.isBlank()) return this
    return filter { entry ->
        buildString {
            append(entry.transaction.title)
            append(' ')
            append(entry.status.label)
            append(' ')
            append(entry.outcome.orEmpty())
            entry.transaction.commands.forEach { command ->
                append(' ')
                append(command.action.label)
                append(' ')
                append(command.candidate.orEmpty())
                append(' ')
                append(command.version.orEmpty())
            }
        }.contains(query, ignoreCase = true)
    }
}
