package io.github.world303.timewallpaper.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.world303.timewallpaper.ui.component.WallpaperThumb
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * Bottom sheet shown when a slot is tapped: pick a new image from the system photo picker, the
 * document picker, or re-use / delete one of the wallpapers cropped earlier.
 */
@Composable
fun MediaPickerSheet(
    show: Boolean,
    recent: List<File>,
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onPickRecent: (String) -> Unit,
    onDeleteRecent: (String) -> Unit,
) {
    var showRecent by remember(show) { mutableStateOf(false) }

    OverlayBottomSheet(
        show = show,
        title = if (showRecent) "最近使用的壁纸" else "设置壁纸",
        onDismissRequest = {
            if (showRecent) showRecent = false else onDismiss()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            if (!showRecent) {
                BasicComponent(
                    title = "从相册选择",
                    summary = "系统照片选择器，只读取你选中的那一张",
                    onClick = onPickGallery,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BasicComponent(
                    title = "从文件选择",
                    summary = "通过文件管理器挑选图片",
                    onClick = onPickFile,
                )
                if (recent.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "最近使用的壁纸",
                        summary = "${recent.size} 张已裁剪的壁纸",
                        onClick = { showRecent = true },
                    )
                }
            } else {
                if (recent.isEmpty()) {
                    Text(
                        "还没有保存过的壁纸。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recent) { file ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    WallpaperThumb(
                                        file = file,
                                        modifier = Modifier.size(width = 74.dp, height = 146.dp),
                                        cornerRadius = 12.dp,
                                        emptyText = null,
                                    )
                                    IconButton(
                                        onClick = { onDeleteRecent(file.name) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(14.dp),
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                TextButton(text = "使用", onClick = { onPickRecent(file.name) })
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "返回", onClick = { showRecent = false })
                }
            }
            Text(
                text = "壁纸只保存在本机，不会上传。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}
