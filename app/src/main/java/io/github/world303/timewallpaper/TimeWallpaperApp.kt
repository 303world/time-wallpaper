package io.github.world303.timewallpaper

import android.app.Application
import io.github.world303.timewallpaper.core.AppGraph

/**
 * Nothing to keep alive any more: the live wallpaper service is bound by the system for as long as
 * the wallpaper is selected, and the schedule is evaluated inside that service.
 */
class TimeWallpaperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
