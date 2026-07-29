package com.worxbend.zephyr.sdkman

import kotlin.time.Duration

sealed interface SdkmanCommand {
    data object Version : SdkmanCommand
    data object ListCandidates : SdkmanCommand
    data object UpdateCandidateMetadata : SdkmanCommand
    data object SelfUpdate : SdkmanCommand
    data class ListVersions(val candidate: String) : SdkmanCommand
    data class Install(val candidate: String, val version: String) : SdkmanCommand
    data class Uninstall(val candidate: String, val version: String) : SdkmanCommand
    data class SetDefault(val candidate: String, val version: String) : SdkmanCommand
}

interface SdkmanCommandRunner {
    suspend fun run(command: SdkmanCommand, timeout: Duration): SdkmanCommandResult
}

data class SdkmanCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}
