package com.siddharth.cv.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.siddharth.cv.shared.App
import com.siddharth.cv.shared.CvNavState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so force light system-bar icons regardless of the device theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val nav = remember { CvNavState() }
            // System Back pops the in-app stack; at the root it falls through and leaves the app.
            BackHandler(enabled = nav.canGoBack) { nav.back() }
            App(nav = nav)
        }
    }
}
