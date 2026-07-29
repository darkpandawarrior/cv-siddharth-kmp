package com.siddharth.cv.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.siddharth.cv.shared.App

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "kmp-app-template") {
            App()
        }
    }
