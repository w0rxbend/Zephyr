package com.worxbend.zephyr.sdkman

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.worxbend.zephyr.domain.ConnectivityDiagnostic
import com.worxbend.zephyr.domain.ConnectivityOutcome
import com.worxbend.zephyr.domain.ConnectivityRouteKind
import com.worxbend.zephyr.domain.boundedConnectivityLatencyMillis
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
        execute(command, timeout, sanitizedEnvironment())

    override suspend fun diagnoseConnectivity(timeout: Duration): ConnectivityDiagnostic {
        val environment = sanitizedEnvironment()
        val route = if (!environment[CANONICAL_HTTPS_PROXY].isNullOrBlank()) {
            ConnectivityRouteKind.Proxy
        } else {
            ConnectivityRouteKind.Direct
        }
        val startedAtNanos = System.nanoTime()
        val result = execute(SdkmanCommand.ConnectivityProbe(route), timeout, environment)
        val latencyMillis = boundedConnectivityLatencyMillis((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
        return ConnectivityDiagnostic(
            route = route,
            checkedAtEpochMillis = System.currentTimeMillis(),
            latencyMillis = latencyMillis,
            outcome = classifyConnectivity(result),
        )
    }

    private suspend fun execute(
        command: SdkmanCommand,
        timeout: Duration,
        environment: Map<String, String>,
    ): SdkmanCommandResult =
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
                if (command is SdkmanCommand.ConnectivityProbe) {
                    ZephyrLogger.warn("SDKMAN connectivity diagnostic failed before completion.")
                } else {
                    ZephyrLogger.warn("SDKMAN command failed before completion: $command", exception)
                }
                return@withContext SdkmanCommandResult(
                    exitCode = -1,
                    stdout = stdout.toString(Charsets.UTF_8.name()).stripAnsi(),
                    stderr = listOf(stderr.toString(Charsets.UTF_8.name()), exception.message.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .stripAnsi(),
                    timedOut = watchdog.killedProcess(),
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
                if (command is SdkmanCommand.ConnectivityProbe) {
                    ZephyrLogger.warn("SDKMAN connectivity diagnostic exited with $exitCode.")
                } else {
                    ZephyrLogger.warn("SDKMAN command exited with $exitCode: $command\n${stderr.toString(Charsets.UTF_8.name()).stripAnsi()}")
                }
            }
            SdkmanCommandResult(
                exitCode = if (watchdog.killedProcess()) -1 else exitCode,
                stdout = stdout.toString(Charsets.UTF_8.name()).stripAnsi(),
                stderr = (listOf(stderr.toString(Charsets.UTF_8.name())) + diagnosticMessages)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .stripAnsi(),
                timedOut = watchdog.killedProcess(),
            )
        }

    private fun sanitizedEnvironment(): Map<String, String> {
        val environment = System.getenv().toMutableMap()
        ALL_PROXY_ENVIRONMENT_KEYS.forEach(environment::remove)
        proxyEnvironment()
            .filterKeys { it in PROXY_ENVIRONMENT_KEYS }
            .filterValues { it.isNotBlank() }
            .forEach(environment::put)
        preferredProxy(environment)?.let { proxy ->
            environment[CANONICAL_HTTPS_PROXY] = proxy
            environment[CANONICAL_HTTPS_PROXY_LOWERCASE] = proxy
        }
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
        return environment
    }

    private fun shellCommand(command: SdkmanCommand): String {
        val sdkmanInit = sdkmanHome / "bin" / "sdkman-init.sh"
        val invocation = if (command is SdkmanCommand.ConnectivityProbe) {
            command.arguments()
        } else {
            "sdk ${command.arguments()}"
        }
        return "source ${sdkmanInit.toString().shellQuote()} && $invocation"
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
            is SdkmanCommand.ConnectivityProbe -> when (route) {
                ConnectivityRouteKind.Direct ->
                    "curl --disable --silent --show-error --output /dev/null --write-out '%{http_code}' --proxy '' --noproxy '*' ${SDKMAN_HEALTH_URL.shellQuote()}"
                ConnectivityRouteKind.Proxy ->
                    "curl --disable --silent --show-error --output /dev/null --write-out '%{http_code}' --noproxy '' ${SDKMAN_HEALTH_URL.shellQuote()}"
            }
        }
}

private fun preferredProxy(environment: Map<String, String>): String? =
    PROXY_SELECTION_ORDER.firstNotNullOfOrNull { key ->
        environment[key]?.takeIf(String::isNotBlank)
    }

internal fun classifyConnectivity(result: SdkmanCommandResult): ConnectivityOutcome {
    val httpCode = result.stdout.trim().takeLast(3).toIntOrNull()
    val normalized = result.output.lowercase()
    return when {
        result.timedOut -> ConnectivityOutcome.Timeout
        httpCode == 407 ||
            "proxy authentication required" in normalized ||
            "proxy auth" in normalized -> ConnectivityOutcome.ProxyAuthentication
        result.exitCode in TLS_CURL_EXIT_CODES ||
            listOf("certificate", "ssl", "tls").any { it in normalized } -> ConnectivityOutcome.Tls
        result.exitCode == CURL_TIMEOUT_EXIT_CODE ||
            "timed out" in normalized ||
            "timeout" in normalized -> ConnectivityOutcome.Timeout
        httpCode != null && httpCode in 200..399 -> ConnectivityOutcome.Online
        httpCode != null && httpCode in 400..599 -> ConnectivityOutcome.Service
        result.exitCode in SERVICE_CURL_EXIT_CODES -> ConnectivityOutcome.Service
        else -> ConnectivityOutcome.Indeterminate
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
private const val SDKMAN_HEALTH_URL = "https://api.sdkman.io/2/healthcheck"
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val CURL_TIMEOUT_EXIT_CODE = 28
private val TLS_CURL_EXIT_CODES = setOf(35, 51, 53, 54, 58, 59, 60, 64, 66, 77, 80, 82, 83, 90, 91)
private val SERVICE_CURL_EXIT_CODES = setOf(5, 6, 7, 22, 52, 55, 56)
private val PROXY_ENVIRONMENT_KEYS = setOf("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy")
private val PROXY_SELECTION_ORDER = listOf("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy")
private const val CANONICAL_HTTPS_PROXY = "HTTPS_PROXY"
private const val CANONICAL_HTTPS_PROXY_LOWERCASE = "https_proxy"
private val ALL_PROXY_ENVIRONMENT_KEYS =
    PROXY_ENVIRONMENT_KEYS + setOf("ALL_PROXY", "all_proxy", "NO_PROXY", "no_proxy")

fun String.stripAnsi(): String =
    replace(ANSI_OSC_SEQUENCE, "")
        .replace(ANSI_CSI_SEQUENCE, "")

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private val ANSI_CSI_SEQUENCE = Regex("\u001B\\[[;?0-9]*[ -/]*[@-~]")
private val ANSI_OSC_SEQUENCE = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
