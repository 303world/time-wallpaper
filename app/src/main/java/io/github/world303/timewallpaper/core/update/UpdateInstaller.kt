package io.github.world303.timewallpaper.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a release APK and hands it to the system package installer. */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"

    fun apkFile(context: Context): File = File(context.cacheDir, "update/time-wallpaper.apk")

    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val target = apkFile(context)
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TimeWallpaper-Android")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) return@withContext Result.failure(IllegalStateException("HTTP $code"))
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            var lastPercent = -1
                            while (input.read(buffer).also { read = it } > 0) {
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (target.length() < 1024) {
                    return@withContext Result.failure(IllegalStateException("下载的文件不完整"))
                }
                Result.success(target)
            } catch (t: Throwable) {
                Log.w(TAG, "download failed", t)
                Result.failure(t)
            }
        }

    fun canInstall(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    fun install(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            false
        }
    }
}
