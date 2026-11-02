package io.github.world303.timewallpaper.core.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.ScheduleEngine
import io.github.world303.timewallpaper.core.image.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Applies the wallpaper of the slot that is active right now.
 *
 * Uses the public [WallpaperManager] API — no Shizuku, no root. When "separate lock screen" is on,
 * the home and lock wallpapers are set independently with FLAG_SYSTEM / FLAG_LOCK.
 *
 * Failure policy: if the slot has no wallpaper (or the file is gone / unreadable), nothing is
 * changed. The previous wallpaper simply stays, which is exactly what the user asked for.
 */
object WallpaperApplier {

    private const val TAG = "WallpaperApplier"

    data class Result(
        val applied: Boolean,
        val slot: Int,
        val message: String? = null,
    )

    fun currentMinute(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Applies the current slot.
     *
     * @param force when false, skips the work if this exact slot + image pair is already applied
     *   (used by alarms and the unlock-time correction pass).
     */
    suspend fun applyCurrent(context: Context, force: Boolean): Result = withContext(Dispatchers.IO) {
        val repo = AppGraph.repo
        val state = repo.state.value

        val minute = currentMinute()
        val slot = ScheduleEngine.slotAt(state.slots, minute)
            ?: return@withContext Result(false, -1, "没有可用的时间段")

        val homeFile = WallpaperStore.fileFor(context, slot.homeImageId)
        val lockFile = if (state.separateLock) WallpaperStore.fileFor(context, slot.lockImageId) else null

        // Strict mode when home and lock are separate: if either side is missing, skip the whole
        // switch and keep the current wallpaper. That is safer than switching half-way and leaving
        // the lock screen showing an older slot.
        if (state.separateLock) {
            if (homeFile == null || lockFile == null) {
                val reason = when {
                    homeFile == null && lockFile == null -> "该时间段还没有设置壁纸，保持当前壁纸"
                    homeFile == null -> "该时间段缺少桌面壁纸，保持当前壁纸"
                    else -> "该时间段缺少锁屏壁纸，保持当前壁纸"
                }
                return@withContext Result(false, slot.startMinute, reason)
            }
        } else if (homeFile == null) {
            return@withContext Result(false, slot.startMinute, "该时间段还没有设置壁纸，保持当前壁纸")
        }

        val alreadyApplied = !force &&
            state.lastAppliedSlot == slot.startMinute &&
            state.lastAppliedHomeId == (homeFile?.name) &&
            state.lastAppliedLockId == (lockFile?.name) &&
            state.lastApplyOk
        if (alreadyApplied) {
            return@withContext Result(false, slot.startMinute, null)
        }

        val wm = WallpaperManager.getInstance(context)
        var appliedAnything = false
        val errors = mutableListOf<String>()

        if (homeFile != null) {
            val bmp = ImageTools.decodeSampled(homeFile, maxDim = 3200)
            if (bmp == null) {
                errors += "桌面壁纸无法解码"
            } else {
                try {
                    // Home only. The lock wallpaper is left completely alone unless the user
                    // explicitly enabled separate lock-screen wallpapers: writing FLAG_LOCK here
                    // would silently overwrite whatever lock wallpaper they had chosen.
                    wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
                    appliedAnything = true
                } catch (t: Throwable) {
                    Log.e(TAG, "set home wallpaper failed", t)
                    errors += "桌面壁纸设置失败"
                } finally {
                    bmp.recycle()
                }
            }
        }

        if (lockFile != null) {
            val bmp = ImageTools.decodeSampled(lockFile, maxDim = 3200)
            if (bmp == null) {
                errors += "锁屏壁纸无法解码"
            } else {
                try {
                    wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
                    appliedAnything = true
                } catch (t: Throwable) {
                    Log.e(TAG, "set lock wallpaper failed", t)
                    errors += "锁屏壁纸设置失败"
                } finally {
                    bmp.recycle()
                }
            }
        }

        repo.update {
            it.copy(
                lastAppliedSlot = slot.startMinute,
                lastAppliedHomeId = homeFile?.name,
                lastAppliedLockId = lockFile?.name,
                lastAppliedAt = System.currentTimeMillis(),
                lastApplyOk = appliedAnything,
                lastApplyMessage = errors.firstOrNull(),
            )
        }

        Result(
            applied = appliedAnything,
            slot = slot.startMinute,
            message = if (appliedAnything) null else errors.firstOrNull() ?: "没有可应用的壁纸",
        )
    }

    /** Clears the separate lock wallpaper so it follows the home wallpaper again. */
    suspend fun clearLockWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "clear lock wallpaper failed", t)
            false
        }
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

    fun recycleQuietly(bitmap: Bitmap?) {
        runCatching { if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() }
    }
}
