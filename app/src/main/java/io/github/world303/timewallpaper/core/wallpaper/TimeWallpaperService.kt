package io.github.world303.timewallpaper.core.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.ScheduleEngine
import io.github.world303.timewallpaper.core.image.ImageTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.max

/**
 * Dynamic wallpaper: the system binds this service for as long as the wallpaper is selected, so
 * there is no alarm, no worker and no foreground service to keep alive.
 *
 * The engine re-evaluates the active slot every 15 seconds, and immediately when the screen turns
 * on, when the phone is unlocked (lock screen vs. home screen show different images) or when the
 * configuration in the app changes.
 */
class TimeWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TimeEngine()

    inner class TimeEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        private var holder: SurfaceHolder? = null
        private var visible = false
        private var lastKey: String? = null

        private var cachedId: String? = null
        private var cachedBitmap: Bitmap? = null

        /** Slot that was on screen when automatic switching got paused. */
        private var frozenSlot: Int? = null

        private val ticker = object : Runnable {
            override fun run() {
                render()
                handler.postDelayed(this, TICK_MS)
            }
        }

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post { render(force = true) }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            holder = surfaceHolder
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                ContextCompat.registerReceiver(
                    this@TimeWallpaperService,
                    screenReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
            // Any edit in the app should be reflected right away instead of waiting for the tick.
            scope.launch {
                runCatching {
                    AppGraph.repo.state.collect { render(force = true) }
                }
            }
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            scope.cancel()
            runCatching { unregisterReceiver(screenReceiver) }
            releaseBitmap()
            holder = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                render()
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, TICK_MS)
            } else {
                handler.removeCallbacks(ticker)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.holder = holder
            lastKey = null
            render(force = true)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            this.holder = null
            lastKey = null
            super.onSurfaceDestroyed(holder)
        }

        private fun render(force: Boolean = false) {
            val surface = holder ?: return
            val state = runCatching { AppGraph.repo.state.value }.getOrNull() ?: return
            val minute = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }

            if (state.autoSwitch) {
                frozenSlot = null
            } else if (frozenSlot == null) {
                frozenSlot = ScheduleEngine.slotAt(state.slots, minute)?.startMinute
            }
            val slot = ScheduleEngine.slotAt(state.slots, frozenSlot ?: minute)
            val locked = runCatching {
                getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
            }.getOrDefault(false)
            val onLockScreen = state.separateLock && locked
            val imageId = if (onLockScreen) slot?.lockImageId else slot?.homeImageId

            val width = surface.surfaceFrame.width()
            val height = surface.surfaceFrame.height()
            if (width <= 0 || height <= 0) return

            val key = "$imageId|$width:$height|$onLockScreen|${slot?.startMinute}"
            if (!force && key == lastKey) return
            lastKey = key

            if (imageId == null) {
                drawSolid(surface, Color.BLACK)
                return
            }
            val file = WallpaperStore.fileFor(this@TimeWallpaperService, imageId)
            if (file == null) {
                drawSolid(surface, Color.BLACK)
                return
            }
            if (imageId == cachedId && cachedBitmap?.isRecycled == false) {
                drawBitmap(surface, cachedBitmap!!)
                return
            }
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { ImageTools.decodeSampled(file, maxDim = max(width, height)) }.getOrNull()
                }
                if (bitmap == null) {
                    drawSolid(surface, Color.BLACK)
                    return@launch
                }
                releaseBitmap()
                cachedBitmap = bitmap
                cachedId = imageId
                drawBitmap(surface, bitmap)
            }
        }

        private fun drawSolid(surface: SurfaceHolder, color: Int) {
            var canvas: Canvas? = null
            try {
                canvas = surface.lockCanvas() ?: return
                canvas.drawColor(color)
            } catch (t: Throwable) {
                Log.w(TAG, "lockCanvas failed", t)
            } finally {
                if (canvas != null) runCatching { surface.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawBitmap(surface: SurfaceHolder, bitmap: Bitmap) {
            if (bitmap.isRecycled) return
            var canvas: Canvas? = null
            try {
                canvas = surface.lockCanvas() ?: return
                val w = canvas.width
                val h = canvas.height
                canvas.drawColor(Color.BLACK)
                if (w > 0 && h > 0) {
                    val scale = max(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
                    val dw = bitmap.width * scale
                    val dh = bitmap.height * scale
                    val left = (w - dw) / 2f
                    val top = (h - dh) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), paint)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "draw failed", t)
            } finally {
                if (canvas != null) runCatching { surface.unlockCanvasAndPost(canvas) }
            }
        }

        private fun releaseBitmap() {
            val old = cachedBitmap
            cachedBitmap = null
            cachedId = null
            runCatching { if (old != null && !old.isRecycled) old.recycle() }
        }
    }

    companion object {
        private const val TAG = "TimeWallpaperService"
        private const val TICK_MS = 15_000L
    }
}
