package com.worxbend.zephyr.sdkman

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.worxbend.zephyr.logging.ZephyrLogger
import okio.Path
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteResultHandler
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.ExecuteWatchdog
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream
import java.time.Duration as JavaDuration
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine

class ApacheCommonsSdkmanCommandRunner(
    private val sdkmanHome: Path,
    private val proxyEnvironment: () -> Map<String, String> = ::emptyMap,
) : SdkmanCommandRunner {
    override suspend fun run(command: SdkmanCommand, timeout: Duration): SdkmanCommandResult =
        withContext(Dispatchers.IO) {
            val stdout = BoundedByteArrayOutputStream(MAX_COMMAND_OUTPUT_BYTES)
            val stderr = BoundedByteArrayOutputStream(MAX_COMMAND_OUTPUT_BYTES)
            val executor = DefaultExecutor.builder()
                .setExecuteStreamHandler(PumpStreamHandler(stdout, stderr))
                .get()
            val watchdog = ExecuteWatchdog.builder()
                .setTimeout(JavaDuration.ofMillis(timeout.inWholeMilliseconds))
                .get()
            executor.watchdog = watchdog
            executor.setExitValues(null)

            val environment = mutableMapOf<String, String>()
            environment.putAll(System.getenv())
            environment.putAll(proxyEnvironment())
            environment["SDKMAN_DIR"] = sdkmanHome.toString()
            environment.remove("BASH_ENV")
            // SDKMAN's human-readable output is parsed below. Keep its structure stable
            // regardless of the desktop session locale.
            environment["LC_ALL"] = "C"
            environment["LANG"] = "C"
            if (environment["TERM"].isNullOrBlank() || environment["TERM"] == "unknown") {
                environment["TERM"] = "xterm-256color"
            }
            environment.putIfAbsent("COLUMNS", "120")
            environment.putIfAbsent("LINES", "40")

            val shell = CommandLine(BASH_PATH)
            // SDKMAN is a shell function, so a shell is unavoidable. The explicit Bash
            // path and cleared BASH_ENV prevent user startup scripts from being evaluated.
            shell.addArgument("--noprofile")
            shell.addArgument("--norc")
            shell.addArgument("-c")
            shell.addArgument(shellCommand(command), false)

            var executionFailure: ExecuteException? = null
            val exitCode = try {
                suspendCancellableCoroutine<Int> { continuation ->
                    val handler = object : ExecuteResultHandler {
                        override fun onProcessComplete(exitValue: Int) {
                            continuation.resume(exitValue)
                        }

                        override fun onProcessFailed(exception: ExecuteException) {
                            executionFailure = exception
                            continuation.resume(exception.exitValue)
                        }
                    }
                    continuation.invokeOnCancellation { watchdog.destroyProcess() }
                    if (continuation.isActive) {
                        executor.execute(shell, environment, handler)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ZephyrLogger.warn("SDKMAN command failed before completion: $command", exception)
                return@withContext SdkmanCommandResult(
                    exitCode = -1,
                    stdout = stdout.toString(Charsets.UTF_8.name()).stripAnsi(),
                    stderr = listOf(stderr.toString(Charsets.UTF_8.name()), exception.message.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .stripAnsi(),
                )
            }

            val diagnosticMessages = buildList {
                if (watchdog.killedProcess()) add("Command timed out after $timeout.")
                if (stdout.truncated || stderr.truncated) add("Command output was truncated.")
                executionFailure?.message?.takeIf { it.isNotBlank() }?.let(::add)
            }
            if (watchdog.killedProcess()) {
                ZephyrLogger.warn("SDKMAN command timed out: $command")
            } else if (exitCode != 0) {
                ZephyrLogger.warn("SDKMAN command exited with $exitCode: $command\n${stderr.toString(Charsets.UTF_8.name()).stripAnsi()}")
            }
            SdkmanCommandResult(
                exitCode = if (watchdog.killedProcess()) -1 else exitCode,
                stdout = stdout.toString(Charsets.UTF_8.name()).stripAnsi(),
                stderr = (listOf(stderr.toString(Charsets.UTF_8.name())) + diagnosticMessages)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .stripAnsi(),
            )
        }

    private fun shellCommand(command: SdkmanCommand): String {
        val sdkmanInit = sdkmanHome / "bin" / "sdkman-init.sh"
        return "source ${sdkmanInit.toString().shellQuote()} && sdk ${command.arguments()}"
    }

    private fun SdkmanCommand.arguments(): String =
        when (this) {
            SdkmanCommand.Version -> "version"
            SdkmanCommand.ListCandidates -> "list"
            SdkmanCommand.UpdateCandidateMetadata -> "update"
            SdkmanCommand.SelfUpdate -> "selfupdate"
            is SdkmanCommand.ListVersions -> "list ${candidate.shellQuote()}"
            is SdkmanCommand.Install -> "install ${candidate.shellQuote()} ${version.shellQuote()}"
            is SdkmanCommand.Uninstall -> "uninstall ${candidate.shellQuote()} ${version.shellQuote()}"
            is SdkmanCommand.SetDefault -> "default ${candidate.shellQuote()} ${version.shellQuote()}"
        }
}

private class BoundedByteArrayOutputStream(
    private val limit: Int,
) : ByteArrayOutputStream() {
    var truncated: Boolean = false
        private set

    override fun write(value: Int) {
        if (count < limit) super.write(value) else truncated = true
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length) { "Invalid byte array range." }
        val writable = (limit - count).coerceAtLeast(0).coerceAtMost(length)
        if (writable > 0) super.write(buffer, offset, writable)
        if (writable < length) truncated = true
    }
}

private const val MAX_COMMAND_OUTPUT_BYTES = 1_048_576
private const val BASH_PATH = "/bin/bash"

fun String.stripAnsi(): String =
    replace(ANSI_OSC_SEQUENCE, "")
        .replace(ANSI_CSI_SEQUENCE, "")

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private val ANSI_CSI_SEQUENCE = Regex("\u001B\\[[;?0-9]*[ -/]*[@-~]")
private val ANSI_OSC_SEQUENCE = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
