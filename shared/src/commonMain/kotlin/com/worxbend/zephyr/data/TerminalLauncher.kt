package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.InstallTarget

data class TerminalLaunchResult(
    val launched: Boolean,
    val message: String,
)

interface TerminalLauncher {
    fun launch(
        sdkmanHome: String,
        candidate: String,
        version: String,
    ): TerminalLaunchResult

    fun launchWorkspace(
        sdkmanHome: String,
        workingDirectory: String,
        targets: List<InstallTarget>,
    ): TerminalLaunchResult
}

expect fun createTerminalLauncher(): TerminalLauncher
