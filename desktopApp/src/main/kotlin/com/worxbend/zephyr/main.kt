package com.worxbend.zephyr

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(width = 1280.dp, height = 820.dp),
        title = "Zephyr",
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(1040, 680)
        }
        App()
    }
}
