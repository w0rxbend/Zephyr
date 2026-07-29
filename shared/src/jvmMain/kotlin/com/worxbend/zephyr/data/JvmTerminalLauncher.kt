package com.worxbend.zephyr.data

import java.io.File

internal class JvmTerminalLauncher(
    private val terminalCandidates: List<File> = DEFAULT_TERMINALS.map(::File),
    private val bash: File = File("/usr/bin/bash"),
    private val processStarter: (ProcessBuilder) -> Unit = { it.start() },
) : TerminalLauncher {
    override fun launch(
        sdkmanHome: String,
        candidate: String,
        version: String,
    ): TerminalLaunchResult {
        if (sdkmanHome.isBlank() || candidate.isBlank() || version.isBlank()) {
            return TerminalLaunchResult(false, "SDKMAN home, candidate, and version are required.")
        }
        if (listOf(sdkmanHome, candidate, version).any(::containsControlCharacters)) {
            return TerminalLaunchResult(false, "Terminal activation values contain unsupported control characters.")
        }
        if (!bash.canExecute()) {
            return TerminalLaunchResult(false, "Bash is unavailable.")
        }
        val terminal = terminalCandidates.firstOrNull(File::canExecute)
            ?: return TerminalLaunchResult(false, "No supported terminal emulator was found.")
        return runCatching {
            val process = ProcessBuilder(terminalCommand(terminal, bash))
            process.environment()["SDKMAN_DIR"] = sdkmanHome
            process.environment()["ZEPHYR_SDKMAN_CANDIDATE"] = candidate
            process.environment()["ZEPHYR_SDKMAN_VERSION"] = version
            processStarter(process)
            TerminalLaunchResult(true, "Opened an activated terminal.")
        }.getOrElse { failure ->
            TerminalLaunchResult(false, failure.message ?: "Unable to open a terminal.")
        }
    }
}

internal fun terminalCommand(terminal: File, bash: File): List<String> {
    val separator = when (terminal.name) {
        "gnome-terminal", "ptyxis" -> listOf("--")
        "kitty" -> emptyList()
        else -> listOf("-e")
    }
    return listOf(terminal.absolutePath) +
        separator +
        listOf(bash.absolutePath, "-lc", ACTIVATION_SCRIPT)
}

private fun containsControlCharacters(value: String): Boolean =
    value.any { it.code < 32 || it.code == 127 }

private const val ACTIVATION_SCRIPT =
    """source "${'$'}SDKMAN_DIR/bin/sdkman-init.sh" && sdk use "${'$'}ZEPHYR_SDKMAN_CANDIDATE" "${'$'}ZEPHYR_SDKMAN_VERSION" && exec "${'$'}{SHELL:-/bin/bash}" -i"""

private val DEFAULT_TERMINALS = listOf(
    "/usr/bin/xdg-terminal-exec",
    "/usr/bin/ptyxis",
    "/usr/bin/gnome-terminal",
    "/usr/bin/konsole",
    "/usr/bin/alacritty",
    "/usr/bin/kitty",
    "/usr/bin/x-terminal-emulator",
    "/usr/bin/xterm",
)

actual fun createTerminalLauncher(): TerminalLauncher = JvmTerminalLauncher()
