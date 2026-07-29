package com.worxbend.zephyr.data

import com.worxbend.zephyr.domain.InstallTarget
import com.worxbend.zephyr.domain.isValidSdkmanCandidateName
import com.worxbend.zephyr.domain.isValidSdkmanVersion

data class SdkmanRcDocument(
    val fileName: String,
    val targets: List<InstallTarget>,
    val warnings: List<String>,
)

interface ProjectToolchainService {
    suspend fun chooseAndRead(): SdkmanRcDocument?
}

expect fun createProjectToolchainService(): ProjectToolchainService

fun parseSdkmanRc(fileName: String, content: String): SdkmanRcDocument {
    val targets = mutableListOf<InstallTarget>()
    val warnings = mutableListOf<String>()
    content.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        val parts = line.split('=', limit = 2)
        val candidate = parts.firstOrNull()?.trim().orEmpty()
        val version = parts.getOrNull(1)?.trim().orEmpty()
        when {
            parts.size != 2 -> warnings += "Line ${index + 1}: expected candidate=version."
            !isValidSdkmanCandidateName(candidate) -> warnings += "Line ${index + 1}: invalid candidate key."
            !isValidSdkmanVersion(version) -> warnings += "Line ${index + 1}: invalid version identifier."
            targets.any { it.candidate == candidate } -> warnings += "Line ${index + 1}: duplicate $candidate entry ignored."
            else -> targets += InstallTarget(candidate, version)
        }
    }
    return SdkmanRcDocument(fileName, targets, warnings)
}
