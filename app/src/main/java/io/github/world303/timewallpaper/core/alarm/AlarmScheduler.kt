package io.github.world303.timewallpaper.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.ScheduleEngine

/**
 * Schedules exactly one alarm for the next switch point. When it fires, the receiver applies the
 * wallpaper and schedules the following one. This is the whole background story of the app:
 *
 * - no foreground service, no polling, no wake locks
 * - at most 24 wake-ups per day, each lasting a few hundred milliseconds
 * - survives Doze because setExactAndAllowWhileIdle is used (AlarmManager exempts it from the
 *   idle deferral; the system rate limit is one per ~9 minutes, we only need one per hour)
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_SWITCH = "io.github.world303.timewallpaper.action.SWITCH"
    private const val REQUEST_CODE = 1001

    fun nextTriggerAt(context: Context): Long? {
        if (!AppGraph.isReady) AppGraph.init(context)
        val state = AppGraph.repo.state.value
        if (!state.autoSwitch) return null
        return ScheduleEngine.nextSwitchAt(state.slots, System.currentTimeMillis())
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        if (pi == null) return
        val triggerAt = nextTriggerAt(context)
        if (triggerAt == null) {
            am.cancel(pi)
            Log.i(TAG, "auto switch disabled, alarm cancelled")
            return
        }
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.i(TAG, "exact alarm at $triggerAt")
            } else {
                // Without the exact-alarm permission the system may defer us, but the switch still
                // happens eventually. The onboarding/settings screens push the user to grant it.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.w(TAG, "inexact alarm at $triggerAt (no exact alarm permission)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to schedule alarm", t)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        pendingIntent(context)?.let { am.cancel(it) }
    }

    private fun pendingIntent(context: Context): PendingIntent? {
        val intent = Intent(context, WallpaperReceiver::class.java).apply {
            action = ACTION_SWITCH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }
}
