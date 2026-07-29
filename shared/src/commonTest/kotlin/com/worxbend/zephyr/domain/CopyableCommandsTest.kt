package com.worxbend.zephyr.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyableCommandsTest {
    @Test
    fun rendersTypedCommandsWithoutShellExpressions() {
        val command = SdkmanTransaction.Install("java", "21.0.5-tem").commands.single()

        assertEquals("sdk install java 21.0.5-tem", command.copyableCommand())
    }
}
