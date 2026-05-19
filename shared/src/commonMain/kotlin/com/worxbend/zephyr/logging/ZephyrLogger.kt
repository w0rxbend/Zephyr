package com.worxbend.zephyr.logging

expect object ZephyrLogger {
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}
