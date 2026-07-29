package com.worxbend.zephyr.data

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal class JvmClipboardService : ClipboardService {
    override fun copy(text: String): Boolean =
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }.isSuccess
}

actual fun createClipboardService(): ClipboardService = JvmClipboardService()
