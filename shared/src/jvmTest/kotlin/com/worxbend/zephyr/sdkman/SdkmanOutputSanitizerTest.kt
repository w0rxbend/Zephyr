package com.worxbend.zephyr.sdkman

import kotlin.test.Test
import kotlin.test.assertEquals

class SdkmanOutputSanitizerTest {
    @Test
    fun removesColorAndOperatingSystemControlSequences() {
        val output = "\u001B[31mfailed\u001B[0m \u001B]0;malicious title\u0007safely"

        assertEquals("failed safely", output.stripAnsi())
    }

    @Test
    fun removesOscSequencesTerminatedByStringTerminator() {
        val output = "open \u001B]8;;https://example.invalid\u001B\\link\u001B]8;;\u001B\\ closed"

        assertEquals("open link closed", output.stripAnsi())
    }
}
