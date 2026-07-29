package com.worxbend.zephyr.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectToolchainServiceTest {
    @Test
    fun parsesValidSdkmanRcEntriesAndReportsInvalidLines() {
        val document = parseSdkmanRc(
            ".sdkmanrc",
            """
                # Project toolchain
                java=21.0.5-tem
                gradle=8.14
                java=17.0.12-tem
                invalid line
            """.trimIndent(),
        )

        assertEquals(listOf("java", "gradle"), document.targets.map { it.candidate })
        assertEquals(2, document.warnings.size)
        assertTrue(document.warnings.first().contains("duplicate"))
    }
}
