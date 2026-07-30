package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.InstallTarget
import com.worxbend.zephyr.domain.OperationJournalEntry
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.domain.OperationStep
import com.worxbend.zephyr.domain.OperationStepStatus
import com.worxbend.zephyr.domain.PlannedSdkmanCommand
import com.worxbend.zephyr.domain.SdkmanCommandAction
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.UninstallTarget
import com.worxbend.zephyr.domain.UpdateActivationTarget
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmOperationStore(
    private val destination: Path = defaultOperationStorePath(),
    private val sensitivePaths: () -> List<String> = ::defaultSensitiveExportPaths,
    private val retentionLimit: Int = DEFAULT_OPERATION_RETENTION,
) : OperationStore {
    override suspend fun load(): List<OperationJournalEntry> = withContext(Dispatchers.IO) {
        if (!Files.isRegularFile(destination)) return@withContext emptyList()
        parseOperationLedger(Files.readString(destination, StandardCharsets.UTF_8))
            .sortedByDescending(OperationJournalEntry::startedAtEpochMillis)
            .take(retentionLimit)
    }

    override suspend fun save(entries: List<OperationJournalEntry>) = withContext(Dispatchers.IO) {
        val retained = entries
            .sortedByDescending(OperationJournalEntry::startedAtEpochMillis)
            .take(retentionLimit)
        val directory = requireNotNull(destination.parent).toAbsolutePath().normalize()
        Files.createDirectories(directory)
        setOwnerOnlyDirectoryPermissions(directory)
        val temporary = Files.createTempFile(directory, ".task-center-", ".tmp")
        try {
            setOwnerOnlyFilePermissions(temporary)
            val content = renderOperationLedger(retained, sensitivePaths())
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val bytes = content.toByteArray(StandardCharsets.UTF_8)
                var buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            setOwnerOnlyFilePermissions(destination)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

actual fun createOperationStore(): OperationStore = JvmOperationStore()

internal fun renderOperationLedger(
    entries: List<OperationJournalEntry>,
    sensitivePaths: List<String> = emptyList(),
): String = buildString {
    appendLine("$OPERATION_LEDGER_MAGIC\t$OPERATION_LEDGER_VERSION")
    entries.forEach { entry ->
        appendLine(
            listOf(
                "E",
                entry.id.toString(),
                entry.startedAtEpochMillis.toString(),
                entry.completedAtEpochMillis?.toString().orEmpty(),
                entry.status.name,
                entry.transaction.persistenceKind(),
                encodeField(entry.outcome.redactedForPersistence(sensitivePaths)),
            ).joinToString("\t"),
        )
        entry.steps.sortedBy(OperationStep::index).forEach { step ->
            appendLine(
                listOf(
                    "S",
                    entry.id.toString(),
                    step.index.toString(),
                    step.command.action.name,
                    encodeField(step.command.candidate.orEmpty()),
                    encodeField(step.command.version.orEmpty()),
                    step.status.name,
                    step.completedAtEpochMillis?.toString().orEmpty(),
                    encodeField(step.outcome.redactedForPersistence(sensitivePaths)),
                ).joinToString("\t"),
            )
        }
    }
}

internal fun parseOperationLedger(content: String): List<OperationJournalEntry> {
    val lines = content.lineSequence().filter(String::isNotBlank).toList()
    require(lines.firstOrNull() == "$OPERATION_LEDGER_MAGIC\t$OPERATION_LEDGER_VERSION") {
        "Unsupported task-center ledger."
    }
    val rawEntries = linkedMapOf<Long, RawStoredEntry>()
    val steps = mutableMapOf<Long, MutableList<OperationStep>>()
    lines.drop(1).forEach { line ->
        val fields = line.split('\t')
        when (fields.firstOrNull()) {
            "E" -> {
                if (fields.size != 7) return@forEach
                runCatching {
                    val id = fields[1].toLong()
                    rawEntries[id] = RawStoredEntry(
                        id = id,
                        startedAtEpochMillis = fields[2].toLong(),
                        completedAtEpochMillis = fields[3].takeIf(String::isNotEmpty)?.toLong(),
                        status = OperationStatus.valueOf(fields[4]),
                        transactionKind = fields[5],
                        outcome = decodeField(fields[6]).ifEmpty { null },
                    )
                }
            }
            "S" -> {
                if (fields.size != 9) return@forEach
                runCatching {
                    val entryId = fields[1].toLong()
                    steps.getOrPut(entryId, ::mutableListOf) += OperationStep(
                        index = fields[2].toInt(),
                        command = PlannedSdkmanCommand(
                            action = SdkmanCommandAction.valueOf(fields[3]),
                            candidate = decodeField(fields[4]).ifEmpty { null },
                            version = decodeField(fields[5]).ifEmpty { null },
                        ),
                        status = OperationStepStatus.valueOf(fields[6]),
                        completedAtEpochMillis = fields[7].takeIf(String::isNotEmpty)?.toLong(),
                        outcome = decodeField(fields[8]).ifEmpty { null },
                    )
                }
            }
        }
    }
    return rawEntries.values.mapNotNull { stored ->
        val storedSteps = steps[stored.id].orEmpty().sortedBy(OperationStep::index)
        val transaction = transactionFromStored(stored.transactionKind, storedSteps.map(OperationStep::command))
            ?: return@mapNotNull null
        OperationJournalEntry(
            id = stored.id,
            transaction = transaction,
            startedAtEpochMillis = stored.startedAtEpochMillis,
            completedAtEpochMillis = stored.completedAtEpochMillis,
            status = stored.status,
            outcome = stored.outcome,
            steps = storedSteps,
        )
    }
}

private fun SdkmanTransaction.persistenceKind(): String =
    when (this) {
        is SdkmanTransaction.Install -> "install"
        is SdkmanTransaction.BatchInstall -> "batch-install"
        is SdkmanTransaction.SnapshotRestore -> "snapshot-restore"
        is SdkmanTransaction.ToolchainActivation ->
            "profile-activation:${encodeField(profileName.trim().take(MAX_PERSISTED_PROFILE_NAME_LENGTH))}"
        is SdkmanTransaction.UpdateActivation -> "update-activation"
        is SdkmanTransaction.Uninstall -> "uninstall"
        is SdkmanTransaction.BatchUninstall -> "batch-uninstall"
        is SdkmanTransaction.SetDefault -> "set-default"
        is SdkmanTransaction.CleanLocalOnly -> "clean-local-only"
        SdkmanTransaction.RefreshMetadata -> "refresh-metadata"
        SdkmanTransaction.SelfUpdate -> "self-update"
    }

private fun transactionFromStored(
    kind: String,
    commands: List<PlannedSdkmanCommand>,
): SdkmanTransaction? =
    runCatching {
        when (kind) {
            "install" -> commands.single().let {
                SdkmanTransaction.Install(requireNotNull(it.candidate), requireNotNull(it.version))
            }
            "batch-install" -> SdkmanTransaction.BatchInstall(
                commands.map {
                    InstallTarget(requireNotNull(it.candidate), requireNotNull(it.version))
                },
            )
            "snapshot-restore" -> SdkmanTransaction.SnapshotRestore(commands)
            "update-activation" -> {
                val installs = commands
                    .filter { it.action == SdkmanCommandAction.Install }
                    .map { requireNotNull(it.candidate) to requireNotNull(it.version) }
                    .toSet()
                SdkmanTransaction.UpdateActivation(
                    commands
                        .filter { it.action == SdkmanCommandAction.SetDefault }
                        .map {
                            UpdateActivationTarget(
                                requireNotNull(it.candidate),
                                requireNotNull(it.version),
                                (it.candidate to it.version) in installs,
                            )
                        },
                )
            }
            "uninstall" -> commands.single().let {
                SdkmanTransaction.Uninstall(requireNotNull(it.candidate), requireNotNull(it.version))
            }
            "batch-uninstall" -> SdkmanTransaction.BatchUninstall(
                commands.map {
                    UninstallTarget(requireNotNull(it.candidate), requireNotNull(it.version))
                },
            )
            "set-default" -> commands.single().let {
                SdkmanTransaction.SetDefault(requireNotNull(it.candidate), requireNotNull(it.version))
            }
            "clean-local-only" -> SdkmanTransaction.CleanLocalOnly(
                candidate = commands.mapNotNull(PlannedSdkmanCommand::candidate).distinct().single(),
                versions = commands.map { requireNotNull(it.version) },
            )
            "refresh-metadata" -> SdkmanTransaction.RefreshMetadata
            "self-update" -> SdkmanTransaction.SelfUpdate
            else -> if (kind.startsWith(PROFILE_ACTIVATION_KIND_PREFIX)) {
                val name = decodeField(kind.removePrefix(PROFILE_ACTIVATION_KIND_PREFIX))
                    .ifBlank { "Recovered profile" }
                SdkmanTransaction.ToolchainActivation(name, commands)
            } else {
                null
            }
        }
    }.getOrNull()

private fun String?.redactedForPersistence(sensitivePaths: List<String>): String =
    orEmpty()
        .let { text ->
            sensitivePaths
                .filter(String::isNotBlank)
                .sortedByDescending(String::length)
                .fold(text) { redacted, path -> redacted.replace(path, "<redacted-path>") }
        }
        .replace(UNIX_ABSOLUTE_PATH, "<redacted-path>")
        .replace(WINDOWS_ABSOLUTE_PATH, "<redacted-path>")
        .take(MAX_PERSISTED_OUTCOME_LENGTH)

private fun encodeField(value: String): String =
    FIELD_ENCODER.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeField(value: String): String =
    String(FIELD_DECODER.decode(value), StandardCharsets.UTF_8)

private fun defaultOperationStorePath(): Path {
    val stateHome = System.getenv("XDG_STATE_HOME")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".local", "state")
    return stateHome.resolve("zephyr").resolve("task-center-v1.ledger")
}

private fun setOwnerOnlyDirectoryPermissions(path: Path) {
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    }
}

private fun setOwnerOnlyFilePermissions(path: Path) {
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }
}

private data class RawStoredEntry(
    val id: Long,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val status: OperationStatus,
    val transactionKind: String,
    val outcome: String?,
)

private val FIELD_ENCODER = Base64.getUrlEncoder().withoutPadding()
private val FIELD_DECODER = Base64.getUrlDecoder()
private val UNIX_ABSOLUTE_PATH = Regex("""(?<![A-Za-z0-9])/(?:[^/\s]+/)*[^/\s,;:]+""")
private val WINDOWS_ABSOLUTE_PATH = Regex("""(?i)\b[A-Z]:\\(?:[^\\\s]+\\)*[^\\\s,;:]+""")
private const val OPERATION_LEDGER_MAGIC = "ZEPHYR_TASK_CENTER"
private const val OPERATION_LEDGER_VERSION = 1
private const val DEFAULT_OPERATION_RETENTION = 250
private const val PROFILE_ACTIVATION_KIND_PREFIX = "profile-activation:"
private const val MAX_PERSISTED_PROFILE_NAME_LENGTH = 120
private const val MAX_PERSISTED_OUTCOME_LENGTH = 1_000
