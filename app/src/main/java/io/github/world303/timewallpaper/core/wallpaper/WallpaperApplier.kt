package io.github.world303.timewallpaper.core.wallpaper

import android.app.WallpaperManager
import android.content.Context
import java.util.Calendar

/**
 * Small helpers shared by the UI. The static wallpaper path (WallpaperManager.setBitmap) is gone:
 * v1.0.5 draws everything from [TimeWallpaperService], so the system never needs a bitmap write.
 */
object WallpaperApplier {

    fun currentMinute(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Target resolution for the cropped wallpaper. We use the real screen size rather than
     * [WallpaperManager.getDesiredMinimumWidth]/Height: several Chinese ROMs report a stale 16:9
     * (or even square) desired size, which would produce a wrong aspect ratio in the crop page.
     */
    fun targetSize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        var w = dm.widthPixels
        var h = dm.heightPixels
        if (w <= 0 || h <= 0) {
            val wm = WallpaperManager.getInstance(context)
            w = wm.desiredMinimumWidth
            h = wm.desiredMinimumHeight
        }
        if (w <= 0 || h <= 0) {
            w = 1080
            h = 2400
        }
        return if (w > h) h to w else w to h
    }
}
