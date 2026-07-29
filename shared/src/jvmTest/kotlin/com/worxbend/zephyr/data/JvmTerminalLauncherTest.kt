package com.worxbend.zephyr.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmTerminalLauncherTest {
    @Test
    fun activationValuesStayOutOfTheShellScript() {
        var started: ProcessBuilder? = null
        val launcher = JvmTerminalLauncher(
            terminalCandidates = listOf(File("/bin/true")),
            bash = File("/bin/true"),
            processStarter = { started = it },
        )

        val result = launcher.launch(
            sdkmanHome = "/tmp/sdkman home",
            candidate = "java; touch /tmp/ignored",
            version = "\$(touch /tmp/ignored)",
        )

        assertTrue(result.launched)
        val process = requireNotNull(started)
        val command = process.command()
        assertFalse(command.last().contains("touch /tmp/ignored"))
        assertEquals("java; touch /tmp/ignored", process.environment()["ZEPHYR_SDKMAN_CANDIDATE"])
        assertEquals("\$(touch /tmp/ignored)", process.environment()["ZEPHYR_SDKMAN_VERSION"])
    }

    @Test
    fun rejectsControlCharactersBeforeStartingAProcess() {
        var started = false
        val launcher = JvmTerminalLauncher(
            terminalCandidates = listOf(File("/bin/true")),
            bash = File("/bin/true"),
            processStarter = { started = true },
        )

        val result = launcher.launch("/tmp/sdkman", "java\ninvalid", "21")

        assertFalse(result.launched)
        assertFalse(started)
    }

    @Test
    fun terminalFamiliesUseTheirSupportedCommandSeparator() {
        assertEquals("--", terminalCommand(File("/usr/bin/ptyxis"), File("/bin/bash"))[1])
        assertEquals("-e", terminalCommand(File("/usr/bin/alacritty"), File("/bin/bash"))[1])
        assertEquals("/bin/bash", terminalCommand(File("/usr/bin/kitty"), File("/bin/bash"))[1])
    }
}
