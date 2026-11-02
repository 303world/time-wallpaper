package io.github.world303.timewallpaper

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.alarm.WallpaperCheckWorker
import io.github.world303.timewallpaper.core.wallpaper.WallpaperApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TimeWallpaperApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Screen-on / unlock is the cheapest reliable correction point: the app process is already
     * alive while the phone is being unlocked, so this always fires even on ROMs that block
     * manifest-registered USER_PRESENT broadcasts (Android 8+ treats it as an implicit broadcast).
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_USER_PRESENT) return
            val appContext = context.applicationContext
            scope.launch {
                runCatching {
                    AppGraph.init(appContext)
                    WallpaperApplier.applyCurrent(appContext, force = false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        registerScreenReceiver()
        schedulePeriodicCheck()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun schedulePeriodicCheck() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<WallpaperCheckWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "wallpaper-check",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
