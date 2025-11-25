package com.example.vitruvianredux

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.vitruvianredux.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Vitruvian Project Phoenix",
            state = WindowState(size = DpSize(1024.dp, 768.dp))
        ) {
            App()
        }
    }
}