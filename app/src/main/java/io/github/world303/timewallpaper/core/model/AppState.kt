package io.github.world303.timewallpaper.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** Default switch points: 00:00, 06:00, 12:00, 18:00. */
val DEFAULT_POINTS: List<Int> = listOf(0, 6 * 60, 12 * 60, 18 * 60)

/**
 * One time slot. Slots are circular: a slot runs from [startMinute] to the next slot's start, and
 * the last one wraps past midnight to the first one. 00:00 is no longer special and can be removed
 * as long as at least one point remains.
 */
@Serializable
data class WallpaperSlot(
    val startMinute: Int,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("imageId")
    val homeImageId: String? = null,
    val lockImageId: String? = null,
) {
    val hasAnyImage: Boolean get() = homeImageId != null || lockImageId != null
}

@Serializable
data class AppState(
    val slots: List<WallpaperSlot> = DEFAULT_POINTS.map { WallpaperSlot(it) },
    /** Master switch for automatic switching. */
    val autoSwitch: Boolean = true,
    /** Check GitHub releases on launch. */
    val autoCheckUpdate: Boolean = true,
    /** First-run guide completed. */
    val onboarded: Boolean = false,
    /** When true every slot carries a separate lock-screen wallpaper. */
    val separateLock: Boolean = false,
    /** Wallpapers removed from the "recent" picker but still referenced by a slot. */
    val hiddenRecentIds: List<String> = emptyList(),
    /** Start minute of the slot that was last applied to the system wallpaper. */
    val lastAppliedSlot: Int = -1,
    val lastAppliedHomeId: String? = null,
    val lastAppliedLockId: String? = null,
    val lastAppliedAt: Long = 0L,
    val lastApplyOk: Boolean = true,
    val lastApplyMessage: String? = null,
    val lastUpdateCheckAt: Long = 0L,
) {
    fun sortedSlots(): List<WallpaperSlot> = slots.sortedBy { it.startMinute }

    /** Home wallpaper is missing for at least one slot. */
    fun homeMissingCount(): Int = slots.count { it.homeImageId == null }

    /** Lock wallpaper is missing for at least one slot (only meaningful when [separateLock]). */
    fun lockMissingCount(): Int = if (!separateLock) 0 else slots.count { it.lockImageId == null }

    /** True when every slot has all the wallpapers it needs for the current mode. */
    fun isComplete(): Boolean = homeMissingCount() == 0 && lockMissingCount() == 0
}

/** Minimum number of switch points: one slot covering the whole day. */
const val MIN_POINTS: Int = 1

/** Maximum number of switch points in a day. */
const val MAX_POINTS: Int = 24

fun minuteLabel(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun slotRangeLabel(startMinute: Int, endMinute: Int): String {
    val end = if (endMinute >= 1440) endMinute - 1440 else endMinute
    return "${minuteLabel(startMinute)} - ${minuteLabel(end)}"
}
