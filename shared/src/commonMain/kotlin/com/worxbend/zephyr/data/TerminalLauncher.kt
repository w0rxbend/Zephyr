package com.worxbend.zephyr.data

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
}

expect fun createTerminalLauncher(): TerminalLauncher
