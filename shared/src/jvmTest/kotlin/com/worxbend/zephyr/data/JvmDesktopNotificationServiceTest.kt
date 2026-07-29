package com.worxbend.zephyr.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmDesktopNotificationServiceTest {
    @Test
    fun reportsUnsupportedWhenNotifierIsUnavailable() {
        val service = JvmDesktopNotificationService(File("/definitely/missing/notify-send"))

        assertFalse(service.show("Update", "One update is available"))
    }

    @Test
    fun passesArgumentsWithoutShellInterpolation() {
        val service = JvmDesktopNotificationService(File("/bin/true"))

        assertTrue(service.show("Update; ignored", "\$(touch /tmp/ignored)"))
    }
}
