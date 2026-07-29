package com.worxbend.zephyr.sdkman

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class ApacheCommonsSdkmanCommandRunnerTest {
    @Test
    fun sourcesTheConfiguredSdkmanHomeWithoutReadingUserShellConfiguration() = runBlocking {
        withFakeSdkmanHome("""
            sdk() {
                if [ "${'$'}1" = "version" ]; then
                    printf '%s\n' 'SDKMAN! test version'
                fi
            }
        """) { home ->
            val result = ApacheCommonsSdkmanCommandRunner(home).run(SdkmanCommand.Version, 2.seconds)

            assertEquals(0, result.exitCode, result.stderr)
            assertEquals("SDKMAN! test version", result.stdout.trim())
        }
    }

    @Test
    fun forcesAStableLocaleForSdkmanOutputParsing() = runBlocking {
        withFakeSdkmanHome("""
            sdk() {
                printf '%s|%s\n' "${'$'}LC_ALL" "${'$'}LANG"
            }
        """) { home ->
            val result = ApacheCommonsSdkmanCommandRunner(home).run(SdkmanCommand.Version, 2.seconds)

            assertEquals(0, result.exitCode, result.stderr)
            assertEquals("C|C", result.stdout.trim())
        }
    }

    @Test
    fun cancellationReturnsPromptlyAfterTheSdkmanCommandStarts() = runBlocking {
        withFakeSdkmanHome("""
            sdk() {
                touch "${'$'}SDKMAN_DIR/started"
                sleep 30
            }
        """) { home ->
            val command = async(Dispatchers.Default) {
                ApacheCommonsSdkmanCommandRunner(home).run(SdkmanCommand.Version, 1.minutes)
            }
            val started = java.io.File(home.toString(), "started")
            withTimeout(2.seconds) {
                while (!started.exists()) {
                    kotlinx.coroutines.yield()
                }
            }

            command.cancelAndJoin()
            assertTrue(command.isCancelled)
        }
    }

    private suspend fun withFakeSdkmanHome(script: String, block: suspend (Path) -> Unit) {
        val home = Files.createTempDirectory("zephyr-runner-test-").toString().toPath()
        try {
            FileSystem.SYSTEM.createDirectories(home / "bin")
            FileSystem.SYSTEM.write(home / "bin" / "sdkman-init.sh") { writeUtf8(script.trimIndent()) }
            block(home)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }
}
