package com.worxbend.zephyr.sdkman

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.worxbend.zephyr.logging.ZephyrLogger
import okio.Path
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.ExecuteWatchdog
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream
import kotlin.time.Duration

class ApacheCommonsSdkmanCommandRunner(
    private val sdkmanHome: Path,
) : SdkmanCommandRunner {
    override suspend fun run(command: SdkmanCommand, timeout: Duration): SdkmanCommandResult =
        withContext(Dispatchers.IO) {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val executor = DefaultExecutor()
            val watchdog = ExecuteWatchdog(timeout.inWholeMilliseconds)
            executor.watchdog = watchdog
            executor.streamHandler = PumpStreamHandler(stdout, stderr)
            executor.setExitValues(null)

            val environment = mutableMapOf<String, String>()
            environment.putAll(System.getenv())
            environment["SDKMAN_DIR"] = sdkmanHome.toString()
            if (environment["TERM"].isNullOrBlank() || environment["TERM"] == "unknown") {
                environment["TERM"] = "xterm-256color"
            }
            environment.putIfAbsent("COLUMNS", "120")
            environment.putIfAbsent("LINES", "40")

            val shell = CommandLine("zsh")
            shell.addArgument("-c")
            shell.addArgument(shellCommand(command), false)

            val exitCode = try {
                executor.execute(shell, environment)
            } catch (exception: ExecuteException) {
                exception.exitValue
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

            val timeoutMessage = if (watchdog.killedProcess()) "\nCommand timed out after $timeout." else ""
            if (watchdog.killedProcess()) {
                ZephyrLogger.warn("SDKMAN command timed out: $command")
            } else if (exitCode != 0) {
                ZephyrLogger.warn("SDKMAN command exited with $exitCode: $command\n${stderr.toString(Charsets.UTF_8.name()).stripAnsi()}")
            }
            SdkmanCommandResult(
                exitCode = if (watchdog.killedProcess()) -1 else exitCode,
                stdout = stdout.toString(Charsets.UTF_8.name()).stripAnsi(),
                stderr = (stderr.toString(Charsets.UTF_8.name()) + timeoutMessage).stripAnsi(),
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
            is SdkmanCommand.Use -> "use ${candidate.shellQuote()} ${version.shellQuote()}"
            is SdkmanCommand.SetDefault -> "default ${candidate.shellQuote()} ${version.shellQuote()}"
        }
}

fun String.stripAnsi(): String =
    replace(Regex("\u001B\\[[;?0-9]*[ -/]*[@-~]"), "")

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"
