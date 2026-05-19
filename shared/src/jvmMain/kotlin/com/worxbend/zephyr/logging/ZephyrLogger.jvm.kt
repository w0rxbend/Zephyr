package com.worxbend.zephyr.logging

actual object ZephyrLogger {
    actual fun warn(message: String, throwable: Throwable?) {
        System.err.println("[Zephyr][WARN] $message")
        throwable?.printStackTrace(System.err)
    }

    actual fun error(message: String, throwable: Throwable?) {
        System.err.println("[Zephyr][ERROR] $message")
        throwable?.printStackTrace(System.err)
    }
}
