package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.domain.DiskImpactKind
import com.worxbend.zephyr.domain.EstimateConfidence
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.SdkmanTransaction
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JvmSdkmanRepositoryTest {
    @Test
    fun rejectsUnsafeCommandIdentifiersBeforeInvokingSdkman() = runBlocking {
        val runner = RecordingRunner()
        val repository = JvmSdkmanRepository(FileSystem.SYSTEM) { runner }

        val unsafeCandidate = repository.install("../java", "21.0.1-tem")
        val unsafeVersion = repository.uninstall("java", "21; rm -rf /")
        val emptyCleanRequest = repository.cleanLocalOnly("java", emptyList())

        assertFalse(unsafeCandidate.success)
        assertFalse(unsafeVersion.success)
        assertFalse(emptyCleanRequest.success)
        assertEquals(0, runner.commands.size)
    }

    @Test
    fun rejectsUnsafeCandidateBeforeAccessingFilesystem() = runBlocking {
        val repository = JvmSdkmanRepository(FileSystem.SYSTEM) { RecordingRunner() }

        assertFailsWith<IllegalArgumentException> { repository.mergedCandidate("../../etc") }
        Unit
    }

    @Test
    fun invalidatesTheCatalogAfterMetadataRefresh() = runBlocking {
        val runner = RecordingRunner()
        val repository = JvmSdkmanRepository(FileSystem.SYSTEM) { runner }

        repository.catalog(refreshMetadata = false)
        repository.catalog(refreshMetadata = false)
        repository.refreshCandidateMetadata()
        repository.catalog(refreshMetadata = false)

        assertEquals(2, runner.commands.count { it == SdkmanCommand.ListCandidates })
        assertEquals(1, runner.commands.count { it == SdkmanCommand.UpdateCandidateMetadata })
    }

    @Test
    fun acceptsStandardSdkmanIdentifiers() = runBlocking {
        val runner = RecordingRunner()
        val repository = JvmSdkmanRepository(FileSystem.SYSTEM) { runner }

        val outcome = repository.install("java", "21.0.5-tem")

        assertTrue(outcome.success)
        assertEquals(SdkmanCommand.Install("java", "21.0.5-tem"), runner.commands.single())
    }

    @Test
    fun mergesFilesystemVersionsAndProtectsTheDefaultVersionDuringCleanup() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val runner = RecordingRunner()
            val repository = JvmSdkmanRepository(FileSystem.SYSTEM, { home }) { runner }

            assertTrue(repository.detect().isInstalled)
            val installed = repository.installedCandidates()
            val merged = repository.mergedCandidate("java")
            val cleanup = repository.cleanLocalOnly("java", listOf("21.0.5-tem"))

            assertEquals(listOf("java"), installed.map { it.name })
            assertEquals("21.0.5-tem", installed.single().defaultVersion)
            assertEquals(listOf("17.0.1-tem"), merged?.localOnlyVersions)
            assertTrue(merged?.installedVersions?.first { it.version == "17.0.1-tem" }?.isInstalled == true)
            assertFalse(cleanup.success)
            assertFalse(runner.commands.any { it is SdkmanCommand.Uninstall })
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    @Test
    fun ignoresMalformedOrSymlinkedSdkmanEntriesDuringFilesystemDiscovery() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val fileSystem = FileSystem.SYSTEM
            fileSystem.createDirectories(home / "candidates" / "invalid;candidate" / "1.0")
            fileSystem.createDirectories(home / "candidates" / "java" / "invalid;version")
            fileSystem.createDirectories(home / "outside" / "99.0")
            fileSystem.createSymlink(home / "candidates" / "evil-link", home / "candidates" / "java")
            fileSystem.createDirectories(home / "candidates" / "gradle" / "8.0")
            fileSystem.createSymlink(home / "candidates" / "gradle" / "current", home / "outside" / "99.0")
            val repository = JvmSdkmanRepository(fileSystem, { home }) { RecordingRunner() }

            val installed = repository.installedCandidates()

            assertEquals(listOf("java", "gradle"), installed.map { it.name })
            assertEquals(listOf("17.0.1-tem", "21.0.5-tem"), installed.first { it.name == "java" }.installedVersions.map { it.version })
            assertNull(installed.first { it.name == "gradle" }.defaultVersion)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    @Test
    fun cleansOnlyVersionsVerifiedAsLocalOnly() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val runner = RecordingRunner()
            val repository = JvmSdkmanRepository(FileSystem.SYSTEM, { home }) { runner }

            val unverified = repository.cleanLocalOnly("java", listOf("19.0.0-tem"))
            val verified = repository.cleanLocalOnly("java", listOf("17.0.1-tem"))

            assertFalse(unverified.success)
            assertEquals("Only versions confirmed as local-only can be cleaned.", unverified.message)
            assertTrue(verified.success)
            assertEquals(listOf(SdkmanCommand.Uninstall("java", "17.0.1-tem")), runner.commands.filterIsInstance<SdkmanCommand.Uninstall>())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    @Test
    fun estimatesExactCleanupAndMedianInstallDiskImpact() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val fileSystem = FileSystem.SYSTEM
            fileSystem.write(home / "candidates" / "java" / "17.0.1-tem" / "runtime.bin") {
                write(ByteArray(1_024))
            }
            fileSystem.write(home / "candidates" / "java" / "21.0.5-tem" / "runtime.bin") {
                write(ByteArray(3_072))
            }
            val repository = JvmSdkmanRepository(fileSystem, { home }) { RecordingRunner() }

            val cleanup = repository.estimateDiskImpact(
                SdkmanTransaction.CleanLocalOnly("java", listOf("17.0.1-tem", "21.0.5-tem")),
            )
            val install = repository.estimateDiskImpact(
                SdkmanTransaction.Install("java", "25.0.1-tem"),
            )

            assertEquals(DiskImpactKind.Reclaimable, cleanup.kind)
            assertEquals(4_096L, cleanup.bytes)
            assertEquals(EstimateConfidence.Exact, cleanup.confidence)
            assertEquals(DiskImpactKind.Required, install.kind)
            assertEquals(2_048L, install.bytes)
            assertEquals(EstimateConfidence.Estimated, install.confidence)
            assertTrue((install.availableBytes ?: 0) > 0)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    @Test
    fun repositoryBoundaryBlocksProtectedCleanupAndUninstall() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val protected = ProtectedVersion("java", "17.0.1-tem")
            val store = InMemoryProtectedVersionStore(setOf(protected))
            val runner = RecordingRunner()
            val repository = JvmSdkmanRepository(FileSystem.SYSTEM, { home }, store) { runner }

            val uninstall = repository.uninstall("java", "17.0.1-tem")
            val cleanup = repository.cleanLocalOnly("java", listOf("17.0.1-tem"))

            assertFalse(uninstall.success)
            assertFalse(cleanup.success)
            assertTrue(uninstall.message.contains("Unpin"))
            assertTrue(cleanup.message.contains("Unpin"))
            assertFalse(runner.commands.any { it is SdkmanCommand.Uninstall })

            assertTrue(repository.setVersionProtected("java", "17.0.1-tem", false).success)
            assertTrue(repository.cleanLocalOnly("java", listOf("17.0.1-tem")).success)
            assertEquals(
                listOf(SdkmanCommand.Uninstall("java", "17.0.1-tem")),
                runner.commands.filterIsInstance<SdkmanCommand.Uninstall>(),
            )
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    @Test
    fun protectedVersionsPersistAcrossStoreInstances() {
        val preferences = Preferences.userRoot().node("/com/worxbend/zephyr/test/${UUID.randomUUID()}")
        try {
            val protected = setOf(
                ProtectedVersion("java", "21.0.5-tem"),
                ProtectedVersion("gradle", "9.0.0"),
            )

            PreferencesProtectedVersionStore(preferences).save(protected)

            assertEquals(protected, PreferencesProtectedVersionStore(preferences).load())
        } finally {
            preferences.removeNode()
            Preferences.userRoot().flush()
        }
    }

    @Test
    fun refusesCleanupWhenRemoteVersionsCannotBeVerified() = runBlocking {
        val home = Files.createTempDirectory("zephyr-sdkman-test-").toString().toPath()
        try {
            createSdkmanHome(home)
            val runner = FailingVersionsRunner()
            val repository = JvmSdkmanRepository(FileSystem.SYSTEM, { home }) { runner }

            assertFailsWith<IllegalStateException> { repository.mergedCandidate("java") }
            val cleanup = repository.cleanLocalOnly("java", listOf("17.0.1-tem"))

            assertFalse(cleanup.success)
            assertEquals("Unable to verify local-only versions. Try scanning again.", cleanup.message)
            assertFalse(runner.commands.any { it is SdkmanCommand.Uninstall })
        } finally {
            FileSystem.SYSTEM.deleteRecursively(home, mustExist = false)
        }
    }

    private fun createSdkmanHome(home: Path) {
        val fileSystem = FileSystem.SYSTEM
        fileSystem.createDirectories(home / "bin")
        fileSystem.createDirectories(home / "candidates" / "java" / "17.0.1-tem")
        fileSystem.createDirectories(home / "candidates" / "java" / "21.0.5-tem")
        fileSystem.write(home / "bin" / "sdkman-init.sh") { writeUtf8("# test SDKMAN init") }
        fileSystem.createSymlink(home / "candidates" / "java" / "current", home / "candidates" / "java" / "21.0.5-tem")
    }

    private class RecordingRunner : SdkmanCommandRunner {
        val commands = mutableListOf<SdkmanCommand>()

        override suspend fun run(command: SdkmanCommand, timeout: kotlin.time.Duration): SdkmanCommandResult {
            commands += command
            return SdkmanCommandResult(
                exitCode = 0,
                stdout = when (command) {
                    SdkmanCommand.ListCandidates -> CATALOG_OUTPUT
                    is SdkmanCommand.ListVersions -> JAVA_VERSIONS_OUTPUT
                    else -> "Command completed"
                },
                stderr = "",
            )
        }
    }

    private class FailingVersionsRunner : SdkmanCommandRunner {
        val commands = mutableListOf<SdkmanCommand>()

        override suspend fun run(command: SdkmanCommand, timeout: kotlin.time.Duration): SdkmanCommandResult {
            commands += command
            return if (command is SdkmanCommand.ListVersions) {
                SdkmanCommandResult(exitCode = 1, stdout = "", stderr = "network unavailable")
            } else {
                SdkmanCommandResult(exitCode = 0, stdout = "Command completed", stderr = "")
            }
        }
    }

    private companion object {
        const val CATALOG_OUTPUT = """
            ================================================================================
            Available Candidates
            ================================================================================
            Ant (1.10.17)                                            https://ant.apache.org/

            Apache Ant is a Java library and command-line tool.

                                                                   ${'$'} sdk install ant
        """

        const val JAVA_VERSIONS_OUTPUT = """
            Available Java Versions for Linux 64bit
            ================================================================================
             Vendor        | Use | Version      | Dist    | Status     | Identifier
            --------------------------------------------------------------------------------
             Temurin       | >>> | 21.0.5       | tem     |            | 21.0.5-tem
        """
    }
}
