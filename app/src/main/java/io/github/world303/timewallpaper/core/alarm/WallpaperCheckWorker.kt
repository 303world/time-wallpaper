package io.github.world303.timewallpaper.core.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.wallpaper.WallpaperApplier

/**
 * Safety net for aggressive ROM battery managers (MIUI especially): they sometimes drop our exact
 * alarm or freeze the app while the phone is idle. This worker wakes up every 15 minutes, re-arms
 * the alarm if it disappeared, and applies the current slot if the wallpaper is stale.
 *
 * When nothing changed the whole run costs one small JSON read plus a few string comparisons.
 */
class WallpaperCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppGraph.init(context)
        return try {
            // Re-arm in case the system (or the OEM) cancelled our pending alarm.
            runCatching { AlarmScheduler.scheduleNext(context) }
            WallpaperApplier.applyCurrent(context, force = false)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
