package com.worxbend.zephyr

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(width = 1000.dp, height = 700.dp),
        title = "Zephyr",
    ) {
        App()
    }
}
