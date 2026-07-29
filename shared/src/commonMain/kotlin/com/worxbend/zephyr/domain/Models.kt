package com.worxbend.zephyr.domain

data class SdkmanStatus(
    val isInstalled: Boolean,
    val home: String?,
    val cliVersion: String? = null,
    val selfUpdateStatus: SdkmanSelfUpdateStatus = SdkmanSelfUpdateStatus.NotChecked,
    val metadataStatus: CandidateMetadataStatus = CandidateMetadataStatus.NotChecked,
    val reason: String? = null,
)

sealed interface SdkmanSelfUpdateStatus {
    data object NotChecked : SdkmanSelfUpdateStatus
    data object UpToDate : SdkmanSelfUpdateStatus
    data object Updated : SdkmanSelfUpdateStatus
    data class Failed(val message: String) : SdkmanSelfUpdateStatus
}

sealed interface CandidateMetadataStatus {
    data object NotChecked : CandidateMetadataStatus
    data object Refreshing : CandidateMetadataStatus
    data object Refreshed : CandidateMetadataStatus
    data class Failed(val message: String) : CandidateMetadataStatus
}

data class Candidate(
    val name: String,
    val displayName: String,
    val description: String? = null,
    val websiteUrl: String? = null,
    val kind: CandidateKind,
    val installedVersions: List<CandidateVersion>,
    /** The SDKMAN version persisted through the candidate's `current` symlink. */
    val defaultVersion: String?,
    val hasLocalOnlyVersions: Boolean,
    val localOnlyVersionCount: Int,
    val localOnlyVersions: List<String>,
)

enum class CandidateKind {
    Sdk,
    Jdk,
}

data class CandidateVersion(
    val version: String,
    val isInstalled: Boolean,
    val isDefault: Boolean,
    val isRemoteAvailable: Boolean,
)

data class CandidateCatalogItem(
    val name: String,
    val displayName: String,
    val stableVersion: String?,
    val description: String?,
    val websiteUrl: String?,
    val kind: CandidateKind,
    val isInstalled: Boolean,
)

fun List<CandidateCatalogItem>.withInstalledCandidates(candidates: List<Candidate>): List<CandidateCatalogItem> {
    val installedNames = candidates.asSequence().map { it.name }.toSet()
    return map { item -> item.copy(isInstalled = item.name in installedNames) }
}

data class JavaVersion(
    val identifier: String,
    val featureVersion: String,
    val providerCode: String?,
    val providerName: String?,
    val isInstalled: Boolean,
    val isDefault: Boolean,
    val isRemoteAvailable: Boolean,
)

data class JavaVersionGroup(
    val title: String,
    val versions: List<JavaVersion>,
)

data class CommandOutcome(
    val success: Boolean,
    val message: String,
)

fun candidateKindFor(name: String): CandidateKind =
    if (name == "java") CandidateKind.Jdk else CandidateKind.Sdk

fun displayNameFor(name: String): String =
    if (name == "java") "JDK" else name.split('-', '_')
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase() } }

fun javaFeatureVersion(identifier: String?): String? =
    identifier?.substringBefore('.')?.substringBefore('-')?.takeIf { it.isNotBlank() }

fun javaProviderCode(identifier: String?): String? =
    identifier?.substringAfterLast('-', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

fun javaProviderName(code: String?): String? =
    code?.let { jdkVendorKnowledge(it)?.displayName ?: it }

fun CandidateVersion.toJavaVersion(): JavaVersion {
    val providerCode = javaProviderCode(version)
    return JavaVersion(
        identifier = version,
        featureVersion = javaFeatureVersion(version) ?: version,
        providerCode = providerCode,
        providerName = javaProviderName(providerCode),
        isInstalled = isInstalled,
        isDefault = isDefault,
        isRemoteAvailable = isRemoteAvailable,
    )
}
