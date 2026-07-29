package com.worxbend.zephyr.data

import com.worxbend.zephyr.sdkman.ApacheCommonsSdkmanCommandRunner
import com.worxbend.zephyr.sdkman.JvmSdkmanRepository
import com.worxbend.zephyr.sdkman.JvmCandidateMetadataCacheStore
import com.worxbend.zephyr.sdkman.PreferencesProtectedVersionStore
import okio.FileSystem

actual fun createSdkmanRepository(): SdkmanRepository =
    JvmSdkmanRepository(
        fileSystem = FileSystem.SYSTEM,
        protectedVersionStore = PreferencesProtectedVersionStore(),
        candidateCacheStore = JvmCandidateMetadataCacheStore(),
        commandRunnerFactory = { home -> ApacheCommonsSdkmanCommandRunner(home) },
    )
