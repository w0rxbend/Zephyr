package com.worxbend.zephyr.domain

data class ProtectedVersion(
    val candidate: String,
    val version: String,
) {
    init {
        require(isValidSdkmanCandidateName(candidate)) { "Invalid SDKMAN candidate name." }
        require(isValidSdkmanVersion(version)) { "Invalid SDKMAN version identifier." }
    }
}
