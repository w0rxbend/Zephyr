package com.worxbend.zephyr.data

import com.worxbend.zephyr.sdkman.ApacheCommonsSdkmanCommandRunner
import com.worxbend.zephyr.sdkman.JvmSdkmanRepository
import okio.FileSystem

actual fun createSdkmanRepository(): SdkmanRepository =
    JvmSdkmanRepository(
        fileSystem = FileSystem.SYSTEM,
        commandRunnerFactory = { home -> ApacheCommonsSdkmanCommandRunner(home) },
    )
