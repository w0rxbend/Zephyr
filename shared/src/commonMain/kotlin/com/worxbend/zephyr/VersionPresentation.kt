package com.worxbend.zephyr

import com.worxbend.zephyr.domain.CandidateVersion

internal fun statusText(version: CandidateVersion): String =
    buildList {
        if (version.isDefault) add("Default")
        if (version.isInstalled) add("Installed")
        if (version.isRemoteAvailable) add("Available") else add("Local only")
    }.joinToString(" - ")

internal fun List<CandidateVersion>.updateTargets(): List<CandidateVersion> =
    filter { it.isRemoteAvailable && !it.isInstalled }
