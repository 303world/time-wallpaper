package io.github.world303.timewallpaper.ui.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.world303.timewallpaper.core.image.ImageTools
import io.github.world303.timewallpaper.core.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max

/**
 * Full-screen crop editor. The preview box shares the aspect ratio of the real screen, so what the
 * user frames is exactly what becomes the wallpaper.
 *
 * The transform is stored analytically (scale / offset / 90° quarter turns) instead of as an
 * accumulated Matrix. Every gesture recomputes a fresh matrix and clamps it against the rotated
 * bounding box, which is what keeps the image glued to the crop box — no black bars, no jitter.
 */
@Composable
fun CropScreen(
    sourceUri: Uri,
    targetWidth: Int,
    targetHeight: Int,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    title: String = "裁剪壁纸",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var motion by remember { mutableStateOf<CropMotion?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(sourceUri) {
        loading = true
        val decoded = withContext(Dispatchers.IO) {
            ImageTools.decodeSampled(context, sourceUri, maxDim = 2800)
        }
        bitmap = decoded
        loading = false
        if (decoded == null) error = "无法读取这张图片"
    }

    LaunchedEffect(bitmap, previewSize) {
        val bmp = bitmap
        if (bmp != null && previewSize.width > 0 && previewSize.height > 0) {
            motion = CropMotion(bmp, previewSize.width.toFloat(), previewSize.height.toFloat())
        }
    }

    fun save() {
        val bmp = bitmap ?: return
        val m = motion ?: return
        if (saving) return
        saving = true
        error = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val render = ImageTools.renderCrop(
                    source = bmp,
                    matrix = m.matrix(),
                    previewWidth = previewSize.width.toFloat(),
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                ) ?: return@withContext null
                val file = WallpaperStore.newFile(context)
                val ok = ImageTools.saveJpeg(render, file)
                render.recycle()
                if (ok) file.name else null
            }
            saving = false
            if (saved != null) onSaved(saved) else error = "保存失败，请重试"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(MiuixIcons.Back, contentDescription = "取消", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MiuixTheme.textStyles.title3,
                    modifier = Modifier.weight(1f),
                )
                if (saving) {
                    Text(
                        "保存中…",
                        color = Color(0xCCFFFFFF),
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ratio = targetWidth.toFloat() / targetHeight.toFloat()
                val availableW = maxWidth
                val availableH = maxHeight
                val boxW: androidx.compose.ui.unit.Dp
                val boxH: androidx.compose.ui.unit.Dp
                if (availableW / availableH > ratio) {
                    boxH = availableH
                    boxW = boxH * ratio
                } else {
                    boxW = availableW
                    boxH = boxW / ratio
                }

                Box(
                    modifier = Modifier
                        .size(boxW, boxH)
                        .clip(RoundedCornerShape(6.dp))
                        .clipToBounds()
                        .background(Color(0xFF111111))
                        .onSizeChanged { if (it.width > 0 && it.height > 0) previewSize = it },
                ) {
                    val bmp = bitmap
                    val m = motion
                    if (bmp != null && m != null) {
                        CropCanvas(bitmap = bmp, motion = m, modifier = Modifier.fillMaxSize())
                    }
                    if (loading) {
                        Text(
                            "正在读取图片…",
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    if (error != null) {
                        Text(
                            error!!,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "旋转",
                    onClick = { motion?.rotate() },
                    modifier = Modifier.weight(1f),
                    enabled = motion != null,
                )
                TextButton(
                    text = "重置",
                    onClick = { motion?.reset() },
                    modifier = Modifier.weight(1f),
                    enabled = motion != null,
                )
                Button(
                    onClick = { save() },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    enabled = bitmap != null && motion != null && !saving,
                    modifier = Modifier.weight(1.2f),
                ) {
                    Text(if (saving) "保存中" else "保存")
                }
            }
        }
    }
}

/** Transform state of the crop preview: zoom + pan + 90° rotations, all in crop-box pixels. */
class CropMotion(
    val bitmap: Bitmap,
    val boxWidthPx: Float,
    val boxHeightPx: Float,
) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var quarter by mutableIntStateOf(0)
        private set

    init {
        reset()
    }

    private fun rotatedWidth(): Float =
        if (quarter % 2 == 0) bitmap.width.toFloat() else bitmap.height.toFloat()

    private fun rotatedHeight(): Float =
        if (quarter % 2 == 0) bitmap.height.toFloat() else bitmap.width.toFloat()

    // A hair above the exact cover scale: sub-pixel rounding can otherwise leave a 1px seam
    // along one edge after a rotation or a pinch.
    fun minScale(): Float = max(boxWidthPx / rotatedWidth(), boxHeightPx / rotatedHeight()) * 1.002f

    fun reset() {
        quarter = 0
        scale = minScale()
        offsetX = 0f
        offsetY = 0f
        clamp()
    }

    fun rotate() {
        quarter = (quarter + 1) % 4
        val minS = minScale()
        if (scale < minS) scale = minS
        clamp()
    }

    fun setScaleForTest(value: Float) {
        scale = value
        clamp()
    }

    fun gesture(panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) {
        val minS = minScale()
        val maxS = minS * 12f
        val target = (scale * zoom).coerceIn(minS, maxS)
        val realZoom = if (scale > 0f) target / scale else 1f
        if (realZoom != 1f) {
            // Keep the point under the fingers fixed while zooming.
            val focusX = centroidX - boxWidthPx / 2f - offsetX
            val focusY = centroidY - boxHeightPx / 2f - offsetY
            offsetX -= focusX * (realZoom - 1f)
            offsetY -= focusY * (realZoom - 1f)
            scale = target
        }
        offsetX += panX
        offsetY += panY
        clamp()
    }

    private fun clamp() {
        val minS = minScale()
        if (scale < minS) scale = minS
        val maxX = (rotatedWidth() * scale - boxWidthPx) / 2f
        val maxY = (rotatedHeight() * scale - boxHeightPx) / 2f
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    /**
     * Fresh matrix mapping bitmap pixels into crop-box pixels.
     *
     * Build order matters: [Matrix.postXxx] concatenates on the LEFT (M' = X * M), so using
     * post* here would translate before rotating. We set the final translation and prepend the
     * operations instead, giving  T(center + offset) * R * S * T(-bitmapCenter).
     */
    fun matrix(): Matrix {
        val matrix = Matrix()
        matrix.setTranslate(boxWidthPx / 2f + offsetX, boxHeightPx / 2f + offsetY)
        matrix.preRotate(quarter * 90f)
        matrix.preScale(scale, scale)
        matrix.preTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        return matrix
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    motion: CropMotion,
    modifier: Modifier = Modifier,
) {
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG) }
    Canvas(
        modifier = modifier.pointerInput(motion, bitmap) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                motion.gesture(pan.x, pan.y, zoom, centroid.x, centroid.y)
            }
        },
    ) {
        // Reading these states subscribes the draw scope to transform changes.
        @Suppress("UNUSED_EXPRESSION")
        motion.quarter
        @Suppress("UNUSED_EXPRESSION")
        motion.scale
        @Suppress("UNUSED_EXPRESSION")
        motion.offsetX
        @Suppress("UNUSED_EXPRESSION")
        motion.offsetY
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(bitmap, motion.matrix(), paint)
        }
    }
}
