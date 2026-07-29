package com.worxbend.zephyr.data

interface ClipboardService {
    fun copy(text: String): Boolean
}

expect fun createClipboardService(): ClipboardService
