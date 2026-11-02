package io.github.world303.timewallpaper.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.wallpaper.WallpaperApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry point for every event that can require a wallpaper (re)check:
 *
 * - the exact alarm at a switch point,
 * - the user unlocking the phone ([Intent.ACTION_USER_PRESENT]) — this is the self-healing pass:
 *   if a switch was missed while the app was killed, the wallpaper is corrected on the next unlock,
 * - boot / app update / clock or timezone change — the alarm is gone and has to be re-armed.
 *
 * The apply call is cheap when nothing changed (it just compares the last applied ids), so doing it
 * on every unlock costs virtually nothing.
 */
class WallpaperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val appContext = context.applicationContext
        AppGraph.init(appContext)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WallpaperApplier.applyCurrent(appContext, force = false)
            } catch (t: Throwable) {
                Log.e("WallpaperReceiver", "apply failed for $action", t)
            } finally {
                // Always re-arm, even if applying failed: one bad image must never stop the schedule.
                runCatching { AlarmScheduler.scheduleNext(appContext) }
                runCatching { pending.finish() }
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            AlarmScheduler.ACTION_SWITCH,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
