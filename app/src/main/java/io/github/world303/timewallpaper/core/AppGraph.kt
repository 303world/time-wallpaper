package io.github.world303.timewallpaper.core

import android.app.Application
import android.util.Log

/**
 * Tiny service locator. Receivers can be created by the system before any Activity exists, so
 * everything they need must be constructible from a bare Context.
 */
object AppGraph {

    private const val TAG = "AppGraph"

    @Volatile
    private var repoOrNull: AppRepository? = null

    val repo: AppRepository
        get() = repoOrNull ?: error("AppGraph not initialised")

    val isReady: Boolean get() = repoOrNull != null

    fun init(context: android.content.Context) {
        if (repoOrNull != null) return
        synchronized(this) {
            if (repoOrNull != null) return
            val app = context.applicationContext
            repoOrNull = AppRepository(app)
            Log.i(TAG, "repository ready")
        }
    }

    fun init(app: Application) = init(app as android.content.Context)
}
