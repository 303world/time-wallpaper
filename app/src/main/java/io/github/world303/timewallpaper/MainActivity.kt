package io.github.world303.timewallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.alarm.AlarmScheduler
import io.github.world303.timewallpaper.ui.AppRoot
import io.github.world303.timewallpaper.ui.theme.TimeWallpaperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        enableEdgeToEdge()
        setContent {
            TimeWallpaperTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Some ROMs drop alarms while the app is in the background; re-arming is idempotent and
        // costs nothing, so do it every time the user opens the app.
        if (AppGraph.isReady) {
            AlarmScheduler.scheduleNext(this)
        }
    }
}
