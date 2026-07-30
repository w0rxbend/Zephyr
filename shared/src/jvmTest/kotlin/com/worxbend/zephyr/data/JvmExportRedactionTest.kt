package com.worxbend.zephyr.data

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmExportRedactionTest {
    @Test
    fun sensitivePathsIncludePersistedCustomSdkmanHomeOutsideUserHome() {
        assertEquals(
            listOf("/home/alex", "/opt/environment-sdkman", "/srv/custom-sdkman"),
            defaultSensitiveExportPaths(
                userHome = "/home/alex",
                environmentSdkmanHome = "/opt/environment-sdkman",
                configuredSdkmanHome = "/srv/custom-sdkman",
            ),
        )
    }

    @Test
    fun duplicateAndBlankSensitivePathsAreRemoved() {
        assertEquals(
            listOf("/home/alex"),
            defaultSensitiveExportPaths("/home/alex", "", "/home/alex"),
        )
    }
}
