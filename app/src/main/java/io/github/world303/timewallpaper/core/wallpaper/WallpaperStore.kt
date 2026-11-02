package io.github.world303.timewallpaper.core.wallpaper

import android.content.Context
import io.github.world303.timewallpaper.core.model.AppState
import java.io.File

/**
 * Owns the cropped wallpaper files inside the app's private storage. Files are kept private:
 * wallpapers are read by us and handed to the system WallpaperManager, never exposed to the
 * media store, so no storage permission is needed and no UI flicker appears in the gallery.
 */
object WallpaperStore {

    private val SAFE_ID = Regex("^[A-Za-z0-9._-]{1,128}$")

    fun dir(context: Context): File {
        val d = File(context.filesDir, "wallpapers")
        if (!d.isDirectory) d.mkdirs()
        return d
    }

    fun isValidId(id: String?): Boolean = id != null && SAFE_ID.matches(id) && !id.contains("..")

    fun fileFor(context: Context, id: String?): File? {
        if (!isValidId(id)) return null
        val f = File(dir(context), id!!)
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    fun newFile(context: Context, extension: String = "jpg"): File {
        val name = "wp_${System.currentTimeMillis()}_${(1000..9999).random()}.$extension"
        return File(dir(context), name)
    }

    fun list(context: Context): List<File> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.length() > 0 && it.name.endsWith(".jpg", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
    }

    fun delete(context: Context, id: String?): Boolean {
        val f = fileFor(context, id) ?: return false
        return f.delete()
    }

    /**
     * Smart garbage collection for cropped wallpapers.
     *
     * Rules, in priority order:
     * 1. Still referenced by a slot -> never delete.
     * 2. Referenced when the user asked to delete it -> remembered in `hiddenRecentIds`; the
     *    moment the slot stops using it, it is deleted for real (no zombie cache).
     * 3. Everything else -> keep only the newest [keepRecentUnreferenced] files as history.
     *
     * Returns the number of deleted files.
     */
    fun prune(context: Context, state: AppState, keepRecentUnreferenced: Int = 5): Int {
        val referenced = state.slots.flatMap { listOfNotNull(it.homeImageId, it.lockImageId) }.toSet()
        val hidden = state.hiddenRecentIds.toSet()
        var unreferencedKept = 0
        var deleted = 0
        list(context).forEach { file ->
            when {
                file.name in referenced -> Unit
                file.name in hidden -> if (file.delete()) deleted++
                unreferencedKept < keepRecentUnreferenced -> unreferencedKept++
                else -> if (file.delete()) deleted++
            }
        }
        return deleted
    }

    /** Drops ids of files that no longer exist, so the hidden list cannot grow forever. */
    fun liveIds(context: Context): Set<String> = list(context).map { it.name }.toSet()

    fun usedBytes(context: Context): Long = list(context).sumOf { it.length() }
}
