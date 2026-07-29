package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.candidateKindFor
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.isValidSdkmanCandidateName
import com.worxbend.zephyr.domain.isValidSdkmanVersion
import com.worxbend.zephyr.logging.ZephyrLogger
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JvmSdkmanRepository(
    private val fileSystem: FileSystem,
    private val sdkmanHomeResolver: () -> Path = ::defaultSdkmanHome,
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
            .mapNotNull { candidatePath ->
                val metadata = fileSystem.metadataOrNull(candidatePath) ?: return@mapNotNull null
                candidatePath.name.takeIf {
                    metadata.isDirectory && metadata.symlinkTarget == null && isValidCandidate(it)
                }
            }
            .mapNotNull { name ->
                val versions = installedVersionsFor(name)
                if (versions.isEmpty()) return@mapNotNull null
                val default = defaultVersionFor(name)
                val kind = candidateKindFor(name)
                Candidate(
                    name = name,
                    displayName = displayNameFor(name),
                    description = null,
                    websiteUrl = null,
                    kind = kind,
                    installedVersions = versions.map {
                        CandidateVersion(
                            version = it,
                            isInstalled = true,
                            isDefault = it == default,
                            isRemoteAvailable = true,
                        )
                    },
                    defaultVersion = default,
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
        validateCandidate(candidate)
        val result = runner().run(SdkmanCommand.ListVersions(candidate), 20.seconds)
        if (!result.success) {
            val message = "Unable to list versions for $candidate: ${result.output.ifBlank { "exit ${result.exitCode}" }}"
            ZephyrLogger.warn(message)
            throw IllegalStateException(message)
        }
        val parsed = SdkmanListParser.parseVersions(result.stdout)
        if (parsed.isEmpty()) {
            val message = "No versions parsed for $candidate. Output started with: ${result.stdout.take(300)}"
            ZephyrLogger.warn(message)
            throw IllegalStateException(message)
        }
        return parsed
    }

    override suspend fun mergedCandidate(candidate: String): Candidate? {
        validateCandidate(candidate)
        val localVersions = installedVersionsFor(candidate)
        val default = defaultVersionFor(candidate)
        val remote = versions(candidate)
        val remoteByVersion = remote.associateBy { it.version }
        val merged = (remote.map { version ->
            version.copy(
                isInstalled = version.isInstalled || version.version in localVersions,
                isDefault = version.version == default,
                isRemoteAvailable = version.isRemoteAvailable,
            )
        } + localVersions.filterNot { it in remoteByVersion }.map { version ->
            CandidateVersion(
                version = version,
                isInstalled = true,
                isDefault = version == default,
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
            installedVersions = merged,
            defaultVersion = default,
            hasLocalOnlyVersions = localOnly.isNotEmpty(),
            localOnlyVersionCount = localOnly.size,
            localOnlyVersions = localOnly,
        )
    }

    override suspend fun refreshCandidateMetadata(): CommandOutcome {
        catalogCache = null
        return runner().run(SdkmanCommand.UpdateCandidateMetadata, 30.seconds).toOutcome("Candidate metadata refreshed.")
    }

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

    override suspend fun install(candidate: String, version: String): CommandOutcome {
        invalidCommandInput(candidate, version)?.let { return it }
        catalogCache = null
        return runner().run(SdkmanCommand.Install(candidate, version), 10.minutes).toOutcome("Installed $version.")
    }

    override suspend fun uninstall(candidate: String, version: String): CommandOutcome {
        invalidCommandInput(candidate, version)?.let { return it }
        catalogCache = null
        return runner().run(SdkmanCommand.Uninstall(candidate, version), 2.minutes).toOutcome("Uninstalled $version.")
    }

    override suspend fun setDefault(candidate: String, version: String): CommandOutcome {
        invalidCommandInput(candidate, version)?.let { return it }
        return runner().run(SdkmanCommand.SetDefault(candidate, version), 30.seconds).toOutcome("Default set to $version.")
    }

    override suspend fun cleanLocalOnly(candidate: String, versions: List<String>): CommandOutcome {
        if (!isValidCandidate(candidate)) return invalidCandidateOutcome()
        if (versions.isEmpty()) return CommandOutcome(false, "Select at least one version to clean.")
        if (versions.any { !isValidVersion(it) }) return invalidVersionOutcome()
        val default = defaultVersionFor(candidate)
        val requestedVersions = versions.distinct()
        val eligible = requestedVersions.filterNot { it == default }
        if (eligible.size != requestedVersions.size) {
            return CommandOutcome(false, "Choose another default version before cleaning this one.")
        }
        val localOnlyVersions = try {
            mergedCandidate(candidate)?.localOnlyVersions.orEmpty().toSet()
        } catch (exception: IllegalStateException) {
            ZephyrLogger.warn("Unable to verify local-only versions before cleanup for $candidate.", exception)
            return CommandOutcome(false, "Unable to verify local-only versions. Try scanning again.")
        }
        if (eligible.any { it !in localOnlyVersions }) {
            return CommandOutcome(false, "Only versions confirmed as local-only can be cleaned.")
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
        validateCandidate(candidate)
        val candidatePath = home() / "candidates" / candidate
        return fileSystem.listOrNull(candidatePath).orEmpty()
            .mapNotNull { versionPath ->
                val metadata = fileSystem.metadataOrNull(versionPath) ?: return@mapNotNull null
                versionPath.name.takeIf {
                    it != "current" && metadata.isDirectory && metadata.symlinkTarget == null && isValidVersion(it)
                }
            }
            .sorted()
    }

    private fun defaultVersionFor(candidate: String): String? {
        validateCandidate(candidate)
        val currentPath = home() / "candidates" / candidate / "current"
        val metadata = fileSystem.metadataOrNull(currentPath) ?: return null
        val version = metadata.symlinkTarget?.name?.takeIf(::isValidVersion) ?: return null
        val versionPath = home() / "candidates" / candidate / version
        return version.takeIf {
            fileSystem.metadataOrNull(versionPath)?.let { target ->
                target.isDirectory && target.symlinkTarget == null
            } == true
        }
    }

    private fun locateHome(): Path = sdkmanHomeResolver()

    private fun home(): Path = sdkmanHome ?: locateHome().also { sdkmanHome = it }

    private fun runner(): SdkmanCommandRunner =
        commandRunner ?: commandRunnerFactory(home()).also { commandRunner = it }

    private fun invalidCommandInput(candidate: String, version: String): CommandOutcome? =
        when {
            !isValidCandidate(candidate) -> invalidCandidateOutcome()
            !isValidVersion(version) -> invalidVersionOutcome()
            else -> null
        }

    private fun validateCandidate(candidate: String) {
        require(isValidCandidate(candidate)) { "Invalid SDKMAN candidate name." }
    }

    private fun isValidCandidate(candidate: String): Boolean = isValidSdkmanCandidateName(candidate)

    private fun isValidVersion(version: String): Boolean = isValidSdkmanVersion(version)

    private fun invalidCandidateOutcome(): CommandOutcome =
        CommandOutcome(false, "Invalid SDKMAN candidate name.")

    private fun invalidVersionOutcome(): CommandOutcome =
        CommandOutcome(false, "Invalid SDKMAN version identifier.")

    private fun SdkmanCommandResult.toOutcome(successMessage: String): CommandOutcome =
        if (success) {
            CommandOutcome(true, stdout.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: successMessage)
        } else {
            CommandOutcome(false, output.ifBlank { "SDKMAN command failed." })
        }
}

private fun defaultSdkmanHome(): Path {
    val configured = System.getenv("SDKMAN_DIR")?.takeIf { it.isNotBlank() }
    return (configured ?: "${System.getProperty("user.home")}/.sdkman").toPath()
}
