package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.JdkSelection
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.candidateKindFor
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.iconResourceFor
import com.worxbend.zephyr.domain.javaFeatureVersion
import com.worxbend.zephyr.domain.javaProviderCode
import com.worxbend.zephyr.domain.javaProviderName
import com.worxbend.zephyr.logging.ZephyrLogger
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JvmSdkmanRepository(
    private val fileSystem: FileSystem,
    private val commandRunnerFactory: (Path) -> SdkmanCommandRunner,
) : SdkmanRepository {
    private var sdkmanHome: Path? = null
    private var commandRunner: SdkmanCommandRunner? = null
    private var catalogCache: List<CandidateCatalogItem>? = null

    override suspend fun detect(): SdkmanStatus {
        val home = locateHome()
        sdkmanHome = home
        commandRunner = commandRunnerFactory(home)

        val init = home / "bin" / "sdkman-init.sh"
        val candidates = home / "candidates"
        val missing = when {
            fileSystem.metadataOrNull(init) == null -> "Missing SDKMAN init script at $init."
            fileSystem.metadataOrNull(candidates)?.isDirectory != true -> "Missing SDKMAN candidates directory at $candidates."
            else -> null
        }

        return if (missing == null) {
            SdkmanStatus(isInstalled = true, home = home.toString())
        } else {
            SdkmanStatus(isInstalled = false, home = home.toString(), reason = missing)
        }
    }

    override suspend fun cliVersion(): String? {
        val result = runner().run(SdkmanCommand.Version, 8.seconds)
        if (!result.success) {
            ZephyrLogger.warn("Unable to read SDKMAN CLI version: ${result.output.ifBlank { "exit ${result.exitCode}" }}")
        }
        return result.stdout.lineSequence().firstOrNull { it.contains("SDKMAN", ignoreCase = true) }?.trim()
            ?: result.stdout.trim().takeIf { it.isNotBlank() }
    }

    override suspend fun installedCandidates(): List<Candidate> {
        val candidatesPath = home() / "candidates"
        return fileSystem.listOrNull(candidatesPath).orEmpty()
            .filter { path -> fileSystem.metadataOrNull(path)?.isDirectory == true }
            .filterNot { path -> fileSystem.metadataOrNull(path)?.symlinkTarget != null }
            .mapNotNull { candidatePath ->
                val name = candidatePath.name
                val versions = installedVersionsFor(name)
                if (versions.isEmpty()) return@mapNotNull null
                val current = currentVersionFor(name)
                val kind = candidateKindFor(name)
                Candidate(
                    name = name,
                    displayName = displayNameFor(name),
                    description = null,
                    websiteUrl = null,
                    kind = kind,
                    iconResource = iconResourceFor(kind),
                    installedVersions = versions.map {
                        CandidateVersion(
                            version = it,
                            isInstalled = true,
                            isCurrent = it == current,
                            isRemoteAvailable = true,
                        )
                    },
                    currentVersion = current,
                    hasLocalOnlyVersions = false,
                    localOnlyVersionCount = 0,
                    localOnlyVersions = emptyList(),
                )
            }
            .sortedWith(compareBy<Candidate> { it.kind != com.worxbend.zephyr.domain.CandidateKind.Jdk }.thenBy { it.displayName })
    }

    override suspend fun catalog(refreshMetadata: Boolean): List<CandidateCatalogItem> {
        if (!refreshMetadata) catalogCache?.let { return it }
        if (refreshMetadata) {
            val updateOutcome = refreshCandidateMetadata()
            if (!updateOutcome.success) {
                ZephyrLogger.warn("Continuing catalog load after metadata refresh failure: ${updateOutcome.message}")
            }
        }
        val installed = installedCandidates().map { it.name }.toSet()
        val result = runner().run(SdkmanCommand.ListCandidates, 20.seconds)
        if (!result.success) {
            val message = "sdk list failed: ${result.output.ifBlank { "exit ${result.exitCode}" }}"
            ZephyrLogger.warn(message)
            throw IllegalStateException(message)
        }
        val parsed = SdkmanListParser.parseCatalog(result.stdout, installed)
        if (parsed.isEmpty()) {
            val message = "sdk list returned no parseable packages. Output started with: ${result.stdout.take(300)}"
            ZephyrLogger.warn(message)
            throw IllegalStateException(message)
        }
        return parsed.also { catalogCache = it }
    }

    override suspend fun versions(candidate: String): List<CandidateVersion> {
        val result = runner().run(SdkmanCommand.ListVersions(candidate), 20.seconds)
        if (!result.success) {
            ZephyrLogger.warn("Unable to list versions for $candidate: ${result.output.ifBlank { "exit ${result.exitCode}" }}")
            return emptyList()
        }
        val parsed = SdkmanListParser.parseVersions(result.stdout)
        if (parsed.isEmpty()) {
            ZephyrLogger.warn("No versions parsed for $candidate. Output started with: ${result.stdout.take(300)}")
        }
        return parsed
    }

    override suspend fun mergedCandidate(candidate: String): Candidate? {
        val localVersions = installedVersionsFor(candidate)
        val current = currentVersionFor(candidate)
        val remote = versions(candidate)
        val remoteByVersion = remote.associateBy { it.version }
        val merged = (remote.map { version ->
            version.copy(
                isInstalled = version.isInstalled || version.version in localVersions,
                isCurrent = version.isCurrent || version.version == current,
                isRemoteAvailable = version.isRemoteAvailable,
            )
        } + localVersions.filterNot { it in remoteByVersion }.map { version ->
            CandidateVersion(
                version = version,
                isInstalled = true,
                isCurrent = version == current,
                isRemoteAvailable = false,
            )
        }).distinctBy { it.version }.sortedWith(versionComparator())

        val kind = candidateKindFor(candidate)
        val metadata = catalogCache?.firstOrNull { it.name == candidate }
        val localOnly = merged.filter { it.isInstalled && !it.isRemoteAvailable }.map { it.version }
        if (merged.isEmpty() && metadata == null) return null
        return Candidate(
            name = candidate,
            displayName = metadata?.displayName ?: displayNameFor(candidate),
            description = metadata?.description,
            websiteUrl = metadata?.websiteUrl,
            kind = kind,
            iconResource = iconResourceFor(kind),
            installedVersions = merged,
            currentVersion = current,
            hasLocalOnlyVersions = localOnly.isNotEmpty(),
            localOnlyVersionCount = localOnly.size,
            localOnlyVersions = localOnly,
        )
    }

    override suspend fun jdkSelection(): JdkSelection {
        val current = currentVersionFor("java")
        val providerCode = javaProviderCode(current)
        return JdkSelection(
            currentIdentifier = current,
            currentFeatureVersion = javaFeatureVersion(current),
            currentProviderName = javaProviderName(providerCode),
            defaultIdentifier = null,
            defaultFeatureVersion = null,
            defaultProviderName = null,
        )
    }

    override suspend fun refreshCandidateMetadata(): CommandOutcome =
        runner().run(SdkmanCommand.UpdateCandidateMetadata, 30.seconds).toOutcome("Candidate metadata refreshed.")

    override suspend fun selfUpdate(): SdkmanSelfUpdateStatus {
        val result = runner().run(SdkmanCommand.SelfUpdate, 2.minutes)
        val output = result.output.lowercase()
        return when {
            !result.success -> {
                val message = result.output.ifBlank { "SDKMAN self-update failed." }
                ZephyrLogger.warn(message)
                SdkmanSelfUpdateStatus.Failed(message)
            }
            "no update" in output || "up-to-date" in output || "up to date" in output -> SdkmanSelfUpdateStatus.UpToDate
            else -> SdkmanSelfUpdateStatus.Updated
        }
    }

    override suspend fun install(candidate: String, version: String): CommandOutcome =
        runner().run(SdkmanCommand.Install(candidate, version), 10.minutes).toOutcome("Installed $version.")

    override suspend fun uninstall(candidate: String, version: String): CommandOutcome =
        runner().run(SdkmanCommand.Uninstall(candidate, version), 2.minutes).toOutcome("Uninstalled $version.")

    override suspend fun use(candidate: String, version: String): CommandOutcome =
        runner().run(SdkmanCommand.Use(candidate, version), 30.seconds).toOutcome("Using $version.")

    override suspend fun setDefault(candidate: String, version: String): CommandOutcome =
        runner().run(SdkmanCommand.SetDefault(candidate, version), 30.seconds).toOutcome("Default set to $version.")

    override suspend fun cleanLocalOnly(candidate: String, versions: List<String>): CommandOutcome {
        val current = currentVersionFor(candidate)
        val eligible = versions.filterNot { it == current }
        if (eligible.size != versions.size) {
            return CommandOutcome(false, "Switch away from the current version before cleaning it.")
        }
        val failures = mutableListOf<String>()
        eligible.forEach { version ->
            val result = runner().run(SdkmanCommand.Uninstall(candidate, version), 2.minutes)
            if (!result.success) {
                val message = "$version: ${result.output.ifBlank { "failed" }}"
                ZephyrLogger.warn("Failed to clean local-only version for $candidate: $message")
                failures += message
            }
        }
        catalogCache = null
        return if (failures.isEmpty()) {
            CommandOutcome(true, "Cleaned ${eligible.size} local-only version(s).")
        } else {
            CommandOutcome(false, "Cleaned with ${failures.size} failure(s): ${failures.joinToString("; ")}")
        }
    }

    private fun installedVersionsFor(candidate: String): List<String> {
        val candidatePath = home() / "candidates" / candidate
        return fileSystem.listOrNull(candidatePath).orEmpty()
            .filter { it.name != "current" }
            .filter { path -> fileSystem.metadataOrNull(path)?.isDirectory == true }
            .map { it.name }
            .sorted()
    }

    private fun currentVersionFor(candidate: String): String? {
        val currentPath = home() / "candidates" / candidate / "current"
        val metadata = fileSystem.metadataOrNull(currentPath) ?: return null
        return metadata.symlinkTarget?.name ?: if (metadata.isDirectory) currentPath.name else null
    }

    private fun locateHome(): Path {
        val configured = System.getenv("SDKMAN_DIR")?.takeIf { it.isNotBlank() }
        val home = configured ?: "${System.getProperty("user.home")}/.sdkman"
        return home.toPath()
    }

    private fun home(): Path = sdkmanHome ?: locateHome().also { sdkmanHome = it }

    private fun runner(): SdkmanCommandRunner =
        commandRunner ?: commandRunnerFactory(home()).also { commandRunner = it }

    private fun SdkmanCommandResult.toOutcome(successMessage: String): CommandOutcome =
        if (success) {
            CommandOutcome(true, stdout.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: successMessage)
        } else {
            CommandOutcome(false, output.ifBlank { "SDKMAN command failed." })
        }
}
