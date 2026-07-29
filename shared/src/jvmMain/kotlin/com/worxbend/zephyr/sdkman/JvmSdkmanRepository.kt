package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.data.SdkmanRepository
import com.worxbend.zephyr.domain.Candidate
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateVersion
import com.worxbend.zephyr.domain.CommandOutcome
import com.worxbend.zephyr.domain.ConnectivityState
import com.worxbend.zephyr.domain.ConnectivityStatus
import com.worxbend.zephyr.domain.DiskImpactEstimate
import com.worxbend.zephyr.domain.DiskImpactKind
import com.worxbend.zephyr.domain.EstimateConfidence
import com.worxbend.zephyr.domain.IntegrityCheck
import com.worxbend.zephyr.domain.IntegrityCheckId
import com.worxbend.zephyr.domain.IntegrityStatus
import com.worxbend.zephyr.domain.ProtectedVersion
import com.worxbend.zephyr.domain.SdkmanSelfUpdateStatus
import com.worxbend.zephyr.domain.SdkmanStatus
import com.worxbend.zephyr.domain.SdkmanTransaction
import com.worxbend.zephyr.domain.candidateKindFor
import com.worxbend.zephyr.domain.displayNameFor
import com.worxbend.zephyr.domain.isValidSdkmanCandidateName
import com.worxbend.zephyr.domain.isValidSdkmanVersion
import com.worxbend.zephyr.logging.ZephyrLogger
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JvmSdkmanRepository(
    private val fileSystem: FileSystem,
    private val sdkmanHomeResolver: () -> Path = ::defaultSdkmanHome,
    private val protectedVersionStore: ProtectedVersionStore = InMemoryProtectedVersionStore(),
    private val connectivityProbe: suspend () -> Boolean = ::probeSdkmanService,
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

    override suspend fun checkConnectivity(): ConnectivityStatus {
        val checkedAt = System.currentTimeMillis()
        return try {
            if (connectivityProbe()) {
                ConnectivityStatus(
                    state = ConnectivityState.Online,
                    checkedAtEpochMillis = checkedAt,
                    detail = "SDKMAN service is reachable.",
                )
            } else {
                ConnectivityStatus(
                    state = ConnectivityState.Offline,
                    checkedAtEpochMillis = checkedAt,
                    detail = "SDKMAN service is not reachable from this session.",
                )
            }
        } catch (exception: Exception) {
            ZephyrLogger.warn("SDKMAN connectivity check failed: ${exception.message}")
            ConnectivityStatus(
                state = ConnectivityState.Offline,
                checkedAtEpochMillis = checkedAt,
                detail = "SDKMAN service is not reachable from this session.",
            )
        }
    }

    override suspend fun integrityChecks(): List<IntegrityCheck> {
        val sdkmanHome = home()
        val candidatesPath = sdkmanHome / "candidates"
        val missingScripts = REQUIRED_SDKMAN_SCRIPTS.filter { relative ->
            fileSystem.metadataOrNull(sdkmanHome / relative)?.let { metadata ->
                !metadata.isDirectory && metadata.symlinkTarget == null
            } != true
        }
        val candidateEntries = fileSystem.listOrNull(candidatesPath).orEmpty()
        val invalidCandidates = candidateEntries.filter { path ->
            val metadata = fileSystem.metadataOrNull(path)
            !isValidCandidate(path.name) || metadata?.isDirectory != true || metadata.symlinkTarget != null
        }
        val validCandidates = candidateEntries - invalidCandidates.toSet()
        val invalidVersions = validCandidates.flatMap { candidatePath ->
            fileSystem.listOrNull(candidatePath).orEmpty()
                .filter { it.name != "current" }
                .filter { versionPath ->
                    val metadata = fileSystem.metadataOrNull(versionPath)
                    !isValidVersion(versionPath.name) || metadata?.isDirectory != true || metadata.symlinkTarget != null
                }
                .map { "${candidatePath.name}/${it.name}" }
        }
        val invalidDefaultLinks = validCandidates.mapNotNull { candidatePath ->
            val current = candidatePath / "current"
            fileSystem.metadataOrNull(current)?.let {
                candidatePath.name.takeIf { currentLinkTargetVersion(candidatePath, current) == null }
            }
        }

        return listOf(
            IntegrityCheck(
                id = IntegrityCheckId.RequiredScripts,
                title = "Required scripts",
                status = if (missingScripts.isEmpty()) IntegrityStatus.Passed else IntegrityStatus.Failed,
                detail = if (missingScripts.isEmpty()) {
                    "${REQUIRED_SDKMAN_SCRIPTS.size} required SDKMAN scripts are present."
                } else {
                    "Missing or unsafe: ${missingScripts.joinToString()}."
                },
            ),
            IntegrityCheck(
                id = IntegrityCheckId.CandidatesDirectory,
                title = "Candidates directory",
                status = if (fileSystem.metadataOrNull(candidatesPath)?.isDirectory == true) {
                    IntegrityStatus.Passed
                } else {
                    IntegrityStatus.Failed
                },
                detail = if (fileSystem.metadataOrNull(candidatesPath)?.isDirectory == true) {
                    "The SDKMAN candidates directory is available."
                } else {
                    "The SDKMAN candidates directory is missing or unreadable."
                },
            ),
            integrityEntryCheck(
                id = IntegrityCheckId.CandidateEntries,
                title = "Candidate entries",
                invalid = invalidCandidates.map(Path::name),
                successDetail = "${validCandidates.size} candidate director${if (validCandidates.size == 1) "y" else "ies"} passed validation.",
            ),
            integrityEntryCheck(
                id = IntegrityCheckId.VersionEntries,
                title = "Version entries",
                invalid = invalidVersions,
                successDetail = "Installed version directories use valid identifiers and do not escape through symlinks.",
            ),
            IntegrityCheck(
                id = IntegrityCheckId.DefaultLinks,
                title = "Default links",
                status = if (invalidDefaultLinks.isEmpty()) IntegrityStatus.Passed else IntegrityStatus.Failed,
                detail = if (invalidDefaultLinks.isEmpty()) {
                    "Every current link resolves to a local version directory."
                } else {
                    "Broken or escaping current links: ${invalidDefaultLinks.take(MAX_INTEGRITY_DETAIL_ITEMS).joinToString()}."
                },
            ),
        )
    }

    override suspend fun estimateDiskImpact(transaction: SdkmanTransaction): DiskImpactEstimate {
        val available = availableDiskBytes()
        return when (transaction) {
            is SdkmanTransaction.Install -> {
                validateCandidate(transaction.candidate)
                val siblingSizes = installedVersionsFor(transaction.candidate)
                    .mapNotNull { version -> directorySize(home() / "candidates" / transaction.candidate / version) }
                    .filter { it > 0 }
                    .sorted()
                val estimatedBytes = siblingSizes.takeIf { it.isNotEmpty() }?.let(::medianSize)
                DiskImpactEstimate(
                    kind = if (estimatedBytes == null) DiskImpactKind.Unknown else DiskImpactKind.Required,
                    bytes = estimatedBytes,
                    availableBytes = available,
                    confidence = if (estimatedBytes == null) EstimateConfidence.Unknown else EstimateConfidence.Estimated,
                    explanation = if (estimatedBytes == null) {
                        "No local sibling installation is available for a size estimate."
                    } else {
                        "Estimated from the median size of ${siblingSizes.size} installed ${displayNameFor(transaction.candidate)} version(s)."
                    },
                )
            }
            is SdkmanTransaction.BatchInstall -> {
                val estimates = transaction.targets.map { target ->
                    estimateDiskImpact(SdkmanTransaction.Install(target.candidate, target.version))
                }
                val knownBytes = estimates.mapNotNull { it.bytes }
                DiskImpactEstimate(
                    kind = if (knownBytes.size == estimates.size) DiskImpactKind.Required else DiskImpactKind.Unknown,
                    bytes = knownBytes.takeIf { it.size == estimates.size }?.sum(),
                    availableBytes = available,
                    confidence = if (knownBytes.size == estimates.size) {
                        EstimateConfidence.Estimated
                    } else {
                        EstimateConfidence.Unknown
                    },
                    explanation = if (knownBytes.size == estimates.size) {
                        "Combined estimate for ${transaction.targets.size} sequential installs."
                    } else {
                        "One or more selected candidates have no local sibling size evidence."
                    },
                )
            }
            is SdkmanTransaction.Uninstall -> reclaimableEstimate(
                versions = listOf(transaction.version),
                candidate = transaction.candidate,
                availableBytes = available,
            )
            is SdkmanTransaction.BatchUninstall -> {
                val estimates = transaction.targets.map { target ->
                    reclaimableEstimate(
                        versions = listOf(target.version),
                        candidate = target.candidate,
                        availableBytes = available,
                    )
                }
                DiskImpactEstimate(
                    kind = DiskImpactKind.Reclaimable,
                    bytes = estimates.sumOf { it.bytes ?: 0L },
                    availableBytes = available,
                    confidence = EstimateConfidence.Exact,
                    explanation = "Exact combined size of ${transaction.targets.size} selected local version directories.",
                )
            }
            is SdkmanTransaction.CleanLocalOnly -> reclaimableEstimate(
                versions = transaction.versions,
                candidate = transaction.candidate,
                availableBytes = available,
            )
            is SdkmanTransaction.SetDefault,
            SdkmanTransaction.RefreshMetadata,
            -> DiskImpactEstimate(
                kind = DiskImpactKind.None,
                bytes = 0,
                availableBytes = available,
                confidence = EstimateConfidence.Exact,
                explanation = "This operation does not add or remove an installed candidate version.",
            )
            SdkmanTransaction.SelfUpdate -> DiskImpactEstimate(
                kind = DiskImpactKind.Unknown,
                availableBytes = available,
                confidence = EstimateConfidence.Unknown,
                explanation = "SDKMAN does not publish the size of its own update before execution.",
            )
        }
    }

    override suspend fun protectedVersions(): Set<ProtectedVersion> = protectedVersionStore.load()

    override suspend fun setVersionProtected(
        candidate: String,
        version: String,
        protected: Boolean,
    ): CommandOutcome {
        invalidCommandInput(candidate, version)?.let { return it }
        val target = ProtectedVersion(candidate, version)
        val current = protectedVersionStore.load()
        val updated = if (protected) current + target else current - target
        return try {
            protectedVersionStore.save(updated)
            CommandOutcome(
                success = true,
                message = if (protected) "Protected $version from cleanup." else "Removed cleanup protection from $version.",
            )
        } catch (exception: Exception) {
            ZephyrLogger.warn("Unable to persist protected SDKMAN versions.", exception)
            CommandOutcome(false, "Unable to save protected versions: ${exception.message}")
        }
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
        if (defaultVersionFor(candidate) == version) {
            return CommandOutcome(false, "Choose another default before uninstalling $version.")
        }
        if (ProtectedVersion(candidate, version) in protectedVersionStore.load()) {
            return CommandOutcome(false, "Unpin $version before uninstalling it.")
        }
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
        val protected = protectedVersionStore.load()
        if (eligible.any { ProtectedVersion(candidate, it) in protected }) {
            return CommandOutcome(false, "Unpin protected versions before cleaning them.")
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

    private fun reclaimableEstimate(
        versions: List<String>,
        candidate: String,
        availableBytes: Long?,
    ): DiskImpactEstimate {
        validateCandidate(candidate)
        require(versions.all(::isValidVersion)) { "Invalid SDKMAN version identifier." }
        val sizes = versions.map { version ->
            directorySize(home() / "candidates" / candidate / version)
        }
        val total = sizes.filterNotNull().fold(0L, ::safeAdd)
        val exact = sizes.all { it != null }
        return DiskImpactEstimate(
            kind = if (exact) DiskImpactKind.Reclaimable else DiskImpactKind.Unknown,
            bytes = total.takeIf { exact },
            availableBytes = availableBytes,
            confidence = if (exact) EstimateConfidence.Exact else EstimateConfidence.Unknown,
            explanation = if (exact) {
                "Calculated from ${versions.size} installed version director${if (versions.size == 1) "y" else "ies"}."
            } else {
                "One or more installed version directories could not be measured."
            },
        )
    }

    private fun directorySize(root: Path): Long? {
        val rootMetadata = fileSystem.metadataOrNull(root) ?: return null
        if (!rootMetadata.isDirectory || rootMetadata.symlinkTarget != null) return null
        val pending = ArrayDeque<Path>()
        pending.add(root)
        var total = 0L
        var visited = 0
        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val children = fileSystem.listOrNull(directory) ?: return null
            children.forEach { child ->
                visited += 1
                if (visited > MAX_DISK_ESTIMATE_ENTRIES) return null
                val metadata = fileSystem.metadataOrNull(child) ?: return null
                if (metadata.symlinkTarget != null) return@forEach
                if (metadata.isDirectory) {
                    pending.add(child)
                } else {
                    total = safeAdd(total, metadata.size ?: 0L)
                }
            }
        }
        return total
    }

    private fun availableDiskBytes(): Long? =
        runCatching {
            java.nio.file.Files.getFileStore(java.nio.file.Path.of(home().toString())).usableSpace
        }.getOrNull()

    private fun defaultVersionFor(candidate: String): String? {
        validateCandidate(candidate)
        val candidatePath = home() / "candidates" / candidate
        return currentLinkTargetVersion(candidatePath, candidatePath / "current")
    }

    private fun currentLinkTargetVersion(candidatePath: Path, currentPath: Path): String? {
        val symlinkTarget = fileSystem.metadataOrNull(currentPath)?.symlinkTarget ?: return null
        val candidateNio = java.nio.file.Path.of(candidatePath.toString()).toAbsolutePath().normalize()
        val rawTarget = java.nio.file.Path.of(symlinkTarget.toString())
        val resolvedTarget = if (rawTarget.isAbsolute) {
            rawTarget.normalize()
        } else {
            java.nio.file.Path.of(currentPath.parent!!.toString()).resolve(rawTarget).normalize()
        }
        if (resolvedTarget.parent != candidateNio) return null
        val version = resolvedTarget.fileName?.toString()?.takeIf(::isValidVersion) ?: return null
        val targetMetadata = fileSystem.metadataOrNull(candidatePath / version) ?: return null
        return version.takeIf { targetMetadata.isDirectory && targetMetadata.symlinkTarget == null }
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

private fun safeAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun medianSize(sortedSizes: List<Long>): Long {
    val middle = sortedSizes.size / 2
    return if (sortedSizes.size % 2 == 1) {
        sortedSizes[middle]
    } else {
        val lower = sortedSizes[middle - 1]
        val upper = sortedSizes[middle]
        lower + (upper - lower) / 2
    }
}

private fun integrityEntryCheck(
    id: IntegrityCheckId,
    title: String,
    invalid: List<String>,
    successDetail: String,
): IntegrityCheck =
    IntegrityCheck(
        id = id,
        title = title,
        status = if (invalid.isEmpty()) IntegrityStatus.Passed else IntegrityStatus.Warning,
        detail = if (invalid.isEmpty()) {
            successDetail
        } else {
            "Ignored ${invalid.size} malformed or symlinked entr${if (invalid.size == 1) "y" else "ies"}: " +
                "${invalid.take(MAX_INTEGRITY_DETAIL_ITEMS).joinToString()}."
        },
    )

private suspend fun probeSdkmanService(): Boolean = withContext(Dispatchers.IO) {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(SDKMAN_SERVICE_HOST, HTTPS_PORT), CONNECTIVITY_TIMEOUT_MILLIS)
    }
    true
}

private const val MAX_DISK_ESTIMATE_ENTRIES = 1_000_000
private const val SDKMAN_SERVICE_HOST = "api.sdkman.io"
private const val HTTPS_PORT = 443
private const val CONNECTIVITY_TIMEOUT_MILLIS = 1_500
private const val MAX_INTEGRITY_DETAIL_ITEMS = 5
private val REQUIRED_SDKMAN_SCRIPTS = listOf(
    "bin/sdkman-init.sh",
    "src/sdkman-main.sh",
    "src/sdkman-list.sh",
    "src/sdkman-install.sh",
    "src/sdkman-uninstall.sh",
    "src/sdkman-default.sh",
    "src/sdkman-update.sh",
    "src/sdkman-selfupdate.sh",
)

private fun defaultSdkmanHome(): Path {
    val configured = System.getenv("SDKMAN_DIR")?.takeIf { it.isNotBlank() }
    return (configured ?: "${System.getProperty("user.home")}/.sdkman").toPath()
}
