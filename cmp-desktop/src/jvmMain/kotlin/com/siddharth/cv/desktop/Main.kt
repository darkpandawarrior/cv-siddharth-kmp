package com.siddharth.cv.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.siddharth.cv.shared.App
import com.siddharth.cv.shared.data.profile

fun main() =
    application {
        // Compose's default window is 800x600, which is BELOW the shared layout's own 900.dp wide
        // breakpoint, so the desktop actual opened with the whole section nav collapsed and no
        // overflow to reach it: two of twenty routes were clickable until the user resized. Nothing
        // could catch that but launching it, which is why it survived every green build.
        val state = rememberWindowState(size = DpSize(1280.dp, 860.dp))
        // Read off `profile` rather than typed here: the title bar is the one piece of copy on the
        // desktop actual that no screen owns, and it read "kmp-app-template" until 2026-08-31,
        // which is the scaffold this was generated from. Nothing could have caught that except
        // launching it and looking, which is exactly what the README used to hedge about.
        Window(onCloseRequest = ::exitApplication, state = state, title = "${profile.name} - ${profile.title}") {
            App()
        }
    }
