package io.github.world303.timewallpaper.core.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * Everything that talks to the system live-wallpaper picker.
 *
 * `CHANGE_LIVE_WALLPAPER` is the only entry point that jumps straight into the preview page, and it
 * is what dock wallpapers use. On HyperOS/MIUI that activity (`com.android.wallpaper.livepicker/
 * .LiveWallpaperChange`) first checks a MIUI app-op (10045); for every third-party app the op is
 * "ignore", so it calls `finish()` before drawing anything. The activity opens and closes within
 * ~100 ms, which looks exactly like "tapping the button does nothing".
 *
 * The picker list (`LIVE_WALLPAPER_CHOOSER`) has no such check and reaches the very same preview
 * page with one extra tap, so it is the fallback when the direct route is blocked.
 */
object LiveWallpaper {

    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, TimeWallpaperService::class.java)

    /** True when our service is the wallpaper the system is currently showing. */
    fun isCurrent(context: Context): Boolean = runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)
    }.getOrDefault(false)

    /** Direct route: opens the preview page for our service. Returns false if nothing handles it. */
    fun openPreview(context: Context): Boolean {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return start(context, intent)
    }

    /** Picker list, always available. */
    fun openChooser(context: Context): Boolean {
        return start(context, Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }

    private fun start(context: Context, intent: Intent): Boolean {
        val host = context.findActivity() ?: context
        if (host !is android.app.Activity) {
            // Only a non-Activity context needs its own task; an Activity keeps the picker in the
            // current task, which is what makes CHANGE_LIVE_WALLPAPER land on the preview page.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { host.startActivity(intent) }.isSuccess
    }

    private fun Context.findActivity(): Context? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }
}
