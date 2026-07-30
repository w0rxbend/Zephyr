package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.ActivityAction
import com.worxbend.zephyr.domain.ActivityEvent
import com.worxbend.zephyr.domain.ActivitySeverity
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

internal class JvmActivityStore(
    private val destination: Path = defaultActivityStorePath(),
    private val sensitivePaths: () -> List<String> = ::defaultSensitiveExportPaths,
    private val retentionLimit: Int = DEFAULT_ACTIVITY_RETENTION,
) : ActivityStore {
    override suspend fun load(): List<ActivityEvent> = withContext(Dispatchers.IO) {
        if (!Files.isRegularFile(destination)) return@withContext emptyList()
        parseActivityLedger(Files.readString(destination, StandardCharsets.UTF_8))
            .sortedByDescending(ActivityEvent::timestampEpochMillis)
            .take(retentionLimit)
    }

    override suspend fun save(events: List<ActivityEvent>) = withContext(Dispatchers.IO) {
        val retained = events
            .sortedByDescending(ActivityEvent::timestampEpochMillis)
            .take(retentionLimit)
        val directory = requireNotNull(destination.parent).toAbsolutePath().normalize()
        Files.createDirectories(directory)
        setActivityDirectoryPermissions(directory)
        val temporary = Files.createTempFile(directory, ".activity-center-", ".tmp")
        try {
            setActivityFilePermissions(temporary)
            val content = renderActivityLedger(retained, sensitivePaths())
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(content.toByteArray(StandardCharsets.UTF_8))
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
            setActivityFilePermissions(destination)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

actual fun createActivityStore(): ActivityStore = JvmActivityStore()

internal fun renderActivityLedger(
    events: List<ActivityEvent>,
    sensitivePaths: List<String> = emptyList(),
): String = buildString {
    appendLine("$ACTIVITY_LEDGER_MAGIC\t$ACTIVITY_LEDGER_VERSION")
    events.forEach { event ->
        appendLine(
            listOf(
                event.id.toString(),
                event.timestampEpochMillis.toString(),
                event.severity.name,
                event.acknowledged.toString(),
                event.action?.name.orEmpty(),
                encodeActivityField(event.message.redactedActivityMessage(sensitivePaths)),
            ).joinToString("\t"),
        )
    }
}

internal fun parseActivityLedger(content: String): List<ActivityEvent> {
    val lines = content.lineSequence().filter(String::isNotBlank).toList()
    require(lines.firstOrNull() == "$ACTIVITY_LEDGER_MAGIC\t$ACTIVITY_LEDGER_VERSION") {
        "Unsupported activity-center ledger."
    }
    return lines.drop(1).mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != 6) return@mapNotNull null
        runCatching {
            ActivityEvent(
                id = fields[0].toLong().also { require(it >= 0) },
                timestampEpochMillis = fields[1].toLong().also { require(it >= 0) },
                severity = ActivitySeverity.valueOf(fields[2]),
                acknowledged = fields[3].toBooleanStrict(),
                action = fields[4].takeIf(String::isNotEmpty)?.let(ActivityAction::valueOf),
                message = decodeActivityField(fields[5]).trim().also { require(it.isNotEmpty()) },
            )
        }.getOrNull()
    }
}

private fun String.redactedActivityMessage(sensitivePaths: List<String>): String =
    sensitivePaths
        .filter(String::isNotBlank)
        .sortedByDescending(String::length)
        .fold(this) { redacted, path -> redacted.replace(path, "<redacted-path>") }
        .replace(ACTIVITY_UNIX_ABSOLUTE_PATH, "<redacted-path>")
        .replace(ACTIVITY_WINDOWS_ABSOLUTE_PATH, "<redacted-path>")
        .take(MAX_PERSISTED_ACTIVITY_MESSAGE_LENGTH)

private fun encodeActivityField(value: String): String =
    ACTIVITY_FIELD_ENCODER.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeActivityField(value: String): String =
    String(ACTIVITY_FIELD_DECODER.decode(value), StandardCharsets.UTF_8)

private fun defaultActivityStorePath(): Path {
    val stateHome = System.getenv("XDG_STATE_HOME")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".local", "state")
    return stateHome.resolve("zephyr").resolve("activity-center-v1.ledger")
}

private fun setActivityDirectoryPermissions(path: Path) {
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

private fun setActivityFilePermissions(path: Path) {
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }
}

private val ACTIVITY_FIELD_ENCODER = Base64.getUrlEncoder().withoutPadding()
private val ACTIVITY_FIELD_DECODER = Base64.getUrlDecoder()
private val ACTIVITY_UNIX_ABSOLUTE_PATH = Regex("""(?<![A-Za-z0-9])/(?:[^/\s]+/)*[^/\s,;:]+""")
private val ACTIVITY_WINDOWS_ABSOLUTE_PATH = Regex("""(?i)\b[A-Z]:\\(?:[^\\\s]+\\)*[^\\\s,;:]+""")
private const val ACTIVITY_LEDGER_MAGIC = "ZEPHYR_ACTIVITY_CENTER"
private const val ACTIVITY_LEDGER_VERSION = 1
private const val DEFAULT_ACTIVITY_RETENTION = 100
private const val MAX_PERSISTED_ACTIVITY_MESSAGE_LENGTH = 1_000
