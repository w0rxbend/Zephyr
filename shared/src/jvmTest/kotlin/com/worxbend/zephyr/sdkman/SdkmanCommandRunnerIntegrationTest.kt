package com.worxbend.zephyr.sdkman

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SdkmanCommandRunnerIntegrationTest {
    @Test
    fun runsSdkmanListThroughApplicationRunnerWhenSdkmanIsInstalled() = runBlocking {
        val sdkmanHome = (System.getenv("SDKMAN_DIR") ?: "${System.getProperty("user.home")}/.sdkman").toPath()
        val init = java.io.File(sdkmanHome.toString(), "bin/sdkman-init.sh")
        if (!init.exists()) return@runBlocking

        val result = ApacheCommonsSdkmanCommandRunner(sdkmanHome).run(SdkmanCommand.ListCandidates, 20.seconds)

        assertEquals(0, result.exitCode, result.stderr)
        assertTrue(result.stdout.contains("Available Candidates"), result.stdout.take(500))
        assertTrue(result.stdout.contains("sdk install java"), result.stdout.take(500))
        assertTrue(SdkmanListParser.parseCatalog(result.stdout, emptySet()).size > 20)
    }

    @Test
    fun repositoryLoadsBrowseCatalogWhenSdkmanIsInstalled() = runBlocking {
        val sdkmanHome = (System.getenv("SDKMAN_DIR") ?: "${System.getProperty("user.home")}/.sdkman").toPath()
        val init = java.io.File(sdkmanHome.toString(), "bin/sdkman-init.sh")
        if (!init.exists()) return@runBlocking

        val repository = JvmSdkmanRepository(FileSystem.SYSTEM) { home -> ApacheCommonsSdkmanCommandRunner(home) }
        val detected = repository.detect()
        val catalog = repository.catalog(refreshMetadata = true)

        assertTrue(detected.isInstalled)
        assertTrue(catalog.any { it.name == "java" }, "Catalog size: ${catalog.size}")
        assertTrue(catalog.size > 20, "Catalog size: ${catalog.size}")
    }
}
