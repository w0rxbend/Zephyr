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
    val iconResource: String,
    val installedVersions: List<CandidateVersion>,
    val currentVersion: String?,
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
    val isCurrent: Boolean,
    val isRemoteAvailable: Boolean,
)

data class CandidateCatalogItem(
    val name: String,
    val displayName: String,
    val stableVersion: String?,
    val description: String?,
    val websiteUrl: String?,
    val kind: CandidateKind,
    val iconResource: String,
    val isInstalled: Boolean,
)

data class JavaVersion(
    val identifier: String,
    val featureVersion: String,
    val providerCode: String?,
    val providerName: String?,
    val isInstalled: Boolean,
    val isCurrent: Boolean,
    val isRemoteAvailable: Boolean,
)

data class JavaVersionGroup(
    val title: String,
    val versions: List<JavaVersion>,
)

data class JdkSelection(
    val currentIdentifier: String?,
    val currentFeatureVersion: String?,
    val currentProviderName: String?,
    val defaultIdentifier: String?,
    val defaultFeatureVersion: String?,
    val defaultProviderName: String?,
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

fun iconResourceFor(kind: CandidateKind): String =
    if (kind == CandidateKind.Jdk) "ic_jdk_mock" else "ic_package_mock"

fun javaFeatureVersion(identifier: String?): String? =
    identifier?.substringBefore('.')?.substringBefore('-')?.takeIf { it.isNotBlank() }

fun javaProviderCode(identifier: String?): String? =
    identifier?.substringAfterLast('-', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

fun javaProviderName(code: String?): String? =
    when (code) {
        "tem" -> "Eclipse Temurin"
        "zulu" -> "Azul Zulu"
        "amzn" -> "Amazon Corretto"
        "graal" -> "GraalVM"
        "librca" -> "BellSoft Liberica"
        "ms" -> "Microsoft"
        "oracle" -> "Oracle"
        null -> null
        else -> code
    }

fun CandidateVersion.toJavaVersion(): JavaVersion {
    val providerCode = javaProviderCode(version)
    return JavaVersion(
        identifier = version,
        featureVersion = javaFeatureVersion(version) ?: version,
        providerCode = providerCode,
        providerName = javaProviderName(providerCode),
        isInstalled = isInstalled,
        isCurrent = isCurrent,
        isRemoteAvailable = isRemoteAvailable,
    )
}
