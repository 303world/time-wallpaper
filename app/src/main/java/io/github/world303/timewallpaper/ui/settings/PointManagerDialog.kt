package io.github.world303.timewallpaper.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.world303.timewallpaper.core.model.MAX_POINTS
import io.github.world303.timewallpaper.core.model.minuteLabel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Add / edit / remove switch points.
 *
 * - Tapping a point opens the time picker pre-filled with its value, so every point is editable.
 * - Points are a ring; 00:00 is not special. The only restriction is that the last remaining
 *   point cannot be deleted (a day must have at least one slot).
 */
@Composable
fun PointManagerDialog(
    show: Boolean,
    points: List<Int>,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
    onUpdate: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var editing by remember(show) { mutableStateOf<Int?>(null) }
    var isAdding by remember(show) { mutableStateOf(false) }
    var hour by remember(show) { mutableIntStateOf(8) }
    var minute by remember(show) { mutableIntStateOf(0) }
    var error by remember(show) { mutableStateOf<String?>(null) }

    val pickerVisible = editing != null || isAdding

    fun openPicker(source: Int) {
        hour = source / 60
        minute = source % 60
        error = null
        isAdding = source < 0
        editing = if (source >= 0) source else null
    }

    fun closePicker() {
        isAdding = false
        editing = null
        error = null
    }

    fun commit() {
        val value = (hour * 60 + minute).coerceIn(0, 1439)
        val original = editing
        when {
            isAdding && points.contains(value) -> error = "这个时间点已经存在"
            original != null && original == value -> closePicker()
            original != null && points.contains(value) -> error = "这个时间点已经存在"
            else -> {
                if (isAdding) onAdd(value) else if (original != null) onUpdate(original, value)
                closePicker()
            }
        }
    }

    OverlayDialog(
        show = show,
        title = when {
            pickerVisible -> if (isAdding) "添加时间点" else "修改时间点"
            else -> "切换时间点"
        },
        summary = when {
            pickerVisible -> "拖动滚轮选择时间"
            else -> "共 ${points.size} 个时间点 · 最多 $MAX_POINTS 个"
        },
        onDismissRequest = {
            if (pickerVisible) closePicker() else onDismiss()
        },
    ) {
        if (pickerVisible) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberPicker(
                        value = hour,
                        onValueChange = { hour = it },
                        range = 0..23,
                        label = { "%02d 时".format(it) },
                        visibleItemCount = 5,
                        modifier = Modifier.weight(1f),
                    )
                    NumberPicker(
                        value = minute,
                        onValueChange = { minute = it },
                        range = 0..59,
                        label = { "%02d 分".format(it) },
                        visibleItemCount = 5,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(text = "取消", onClick = { closePicker() }, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { commit() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (isAdding) "添加" else "保存")
                    }
                }
            }
        } else {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(points) { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openPicker(point) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = minuteLabel(point),
                                style = MiuixTheme.textStyles.title4,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "删除",
                                onClick = { onRemove(point) },
                                enabled = points.size > 1,
                            )
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = if (points.size >= MAX_POINTS) "已达到 24 个时间点上限" else "＋ 添加时间点",
                    onClick = { if (points.size < MAX_POINTS) openPicker(-1) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = points.size < MAX_POINTS,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("完成")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
