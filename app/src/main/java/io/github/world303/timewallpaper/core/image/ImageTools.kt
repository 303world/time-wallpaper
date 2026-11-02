package io.github.world303.timewallpaper.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Decoding / transform helpers used by the crop page. All methods are blocking, call off the main thread. */
object ImageTools {

    private const val TAG = "ImageTools"

    /** Upper bound for the working bitmap; big enough for a sharp crop, small enough to avoid OOM. */
    private const val MAX_WORKING_DIM = 2800

    data class Decoded(val bitmap: Bitmap, val width: Int, val height: Int)

    /**
     * Decodes [file] with a power-of-two sample size so the longest side stays <= [maxDim],
     * and bakes the EXIF orientation into the bitmap.
     */
    fun decodeSampled(file: File, maxDim: Int = MAX_WORKING_DIM): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            applyExifOrientation(file, raw)
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed for ${file.name}", t)
            null
        }
    }

    fun decodeSampled(context: Context, uri: Uri, maxDim: Int = MAX_WORKING_DIM): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            applyOrientation(raw, orientation)
        } catch (t: Throwable) {
            Log.w(TAG, "decode uri failed", t)
            null
        }
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return src
        }
        return try {
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (rotated !== src) src.recycle()
            rotated
        } catch (t: Throwable) {
            src
        }
    }

    /**
     * Draws [source] into a [targetWidth] x [targetHeight] bitmap using [matrix] expressed in
     * preview coordinates ([previewWidth] wide), scaling everything by targetWidth / previewWidth.
     */
    fun renderCrop(
        source: Bitmap,
        matrix: Matrix,
        previewWidth: Float,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        if (previewWidth <= 0f || targetWidth <= 0 || targetHeight <= 0) return null
        val factor = targetWidth / previewWidth
        val export = Matrix(matrix)
        export.postScale(factor, factor)
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, export, paint)
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "save failed", t)
            runCatching { file.delete() }
            false
        }
    }

    /**
     * Scale factor that makes [bitmap] cover a [boxW] x [boxH] box (center crop, no letterboxing).
     */
    fun coverScale(bitmap: Bitmap, boxW: Float, boxH: Float): Float =
        max(boxW / bitmap.width, boxH / bitmap.height)

    /** Clamps [matrix] so the transformed bitmap always covers the preview box. */
    fun clampMatrix(matrix: Matrix, bitmap: Bitmap, boxW: Float, boxH: Float, minScale: Float) {
        val values = FloatArray(9)
        matrix.getValues(values)
        val currentScale = kotlin.math.sqrt(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_X] + values[Matrix.MSKEW_Y] * values[Matrix.MSKEW_Y])
        if (currentScale < minScale) {
            val fix = minScale / currentScale
            matrix.postScale(fix, fix, boxW / 2f, boxH / 2f)
        }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(rect)
        var dx = 0f
        var dy = 0f
        if (rect.left > 0f) dx = -rect.left
        if (rect.right < boxW) dx = boxW - rect.right
        if (rect.top > 0f) dy = -rect.top
        if (rect.bottom < boxH) dy = boxH - rect.bottom
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }
}
