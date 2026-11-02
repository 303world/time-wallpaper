package io.github.world303.timewallpaper.core

import android.content.Context
import io.github.world303.timewallpaper.core.model.AppState
import io.github.world303.timewallpaper.core.model.WallpaperSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Persists [AppState] as a single JSON document. Writes are atomic (temp file + rename) so a
 * crash or a killed process can never leave a half-written config behind.
 */
class AppRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val file = File(context.filesDir, "app_state.json")
    private val lock = Any()

    private val _state = MutableStateFlow(readFromDisk())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val next = transform(_state.value)
        _state.value = next
        writeToDisk(next)
        return next
    }

    private fun readFromDisk(): AppState {
        return try {
            if (file.isFile) {
                json.decodeFromString<AppState>(file.readText())
            } else {
                AppState()
            }
        } catch (t: Throwable) {
            runCatching { file.copyTo(File(file.parentFile, "app_state.corrupt.json"), overwrite = true) }
            AppState()
        }
    }

    private fun writeToDisk(state: AppState) {
        synchronized(lock) {
            runCatching {
                val tmp = File(file.parentFile, "app_state.json.tmp")
                tmp.writeText(json.encodeToString(AppState.serializer(), state))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }
}

/**
 * Time-slot helpers shared by the UI and the scheduler. Slots form a ring: the first point starts
 * the day and the last slot wraps past midnight back to the first point.
 */
object ScheduleEngine {

    fun sorted(slots: List<WallpaperSlot>): List<WallpaperSlot> = slots.sortedBy { it.startMinute }

    /** Slot covering [minute] (0..1439). Times before the first point belong to the last slot. */
    fun slotAt(slots: List<WallpaperSlot>, minute: Int): WallpaperSlot? {
        val sortedSlots = sorted(slots)
        if (sortedSlots.isEmpty()) return null
        var found = sortedSlots.last()
        for (slot in sortedSlots) {
            if (slot.startMinute <= minute) found = slot else break
        }
        return found
    }

    /** End minute of [slot]; may exceed 1440 for a slot that wraps past midnight. */
    fun endMinuteOf(slots: List<WallpaperSlot>, slot: WallpaperSlot): Int {
        val sortedSlots = sorted(slots)
        val index = sortedSlots.indexOfFirst { it.startMinute == slot.startMinute }
        if (index < 0) return 1440
        return if (index < sortedSlots.lastIndex) sortedSlots[index + 1].startMinute
        else sortedSlots.first().startMinute + 1440
    }

    /** Minutes of the day covered by [slot] (1..1440). */
    fun durationOf(slots: List<WallpaperSlot>, slot: WallpaperSlot): Int =
        (endMinuteOf(slots, slot) - slot.startMinute).coerceIn(1, 1440)

    /** Milliseconds since epoch of the next switch point strictly after [nowMillis]. */
    fun nextSwitchAt(
        slots: List<WallpaperSlot>,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val points = sorted(slots).map { it.startMinute }.ifEmpty { listOf(0) }
        val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val next = points.firstOrNull { it > nowMinute }
        if (next != null) {
            cal.set(Calendar.HOUR_OF_DAY, next / 60)
            cal.set(Calendar.MINUTE, next % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, points.first() / 60)
            cal.set(Calendar.MINUTE, points.first() % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** Human readable "今天 18:00" / "明天 00:00". */
    fun nextSwitchLabel(slots: List<WallpaperSlot>, nowMillis: Long): String {
        val next = nextSwitchAt(slots, nowMillis)
        val cal = Calendar.getInstance().apply { timeInMillis = next }
        val hm = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val today = Calendar.getInstance()
        val sameDay = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) "今天 $hm" else "明天 $hm"
    }
}
