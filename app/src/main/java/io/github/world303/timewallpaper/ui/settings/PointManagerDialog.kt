package io.github.world303.timewallpaper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.world303.timewallpaper.core.model.MAX_POINTS
import io.github.world303.timewallpaper.core.model.minuteLabel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Add / edit / remove switch points.
 *
 * The list and the time editor are two *separate* Dialogs instead of one dialog swapping its
 * content: Miuix caches the dialog content per visible state, so swapping content inside a single
 * dialog could leave the old list on screen (tapping a point appeared to do nothing).
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

    fun openEditor(source: Int) {
        if (source >= 0) {
            hour = source / 60
            minute = source % 60
            isAdding = false
            editing = source
        } else {
            // -1 is the "add" sentinel; without this branch (-1 % 60) would put -1 in the picker.
            hour = 8
            minute = 0
            isAdding = true
            editing = null
        }
        error = null
    }

    fun closeEditor() {
        isAdding = false
        editing = null
        error = null
    }

    fun commit() {
        val value = (hour * 60 + minute).coerceIn(0, 1439)
        val original = editing
        when {
            isAdding && points.contains(value) -> error = "这个时间点已经存在"
            original != null && original == value -> closeEditor()
            original != null && points.contains(value) -> error = "这个时间点已经存在"
            else -> {
                if (isAdding) onAdd(value) else if (original != null) onUpdate(original, value)
                closeEditor()
            }
        }
    }

    // --- list ---------------------------------------------------------------
    OverlayDialog(
        show = show && !pickerVisible,
        title = "切换时间点",
        summary = "共 ${points.size} 个 · 最多 $MAX_POINTS 个 · 点击时间可修改",
        onDismissRequest = onDismiss,
    ) {
        Column {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(points) { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openEditor(point) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = minuteLabel(point),
                            style = MiuixTheme.textStyles.title4,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "修改",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp),
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
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = if (points.size >= MAX_POINTS) "已达到 24 个时间点上限" else "＋ 添加时间点",
                onClick = { if (points.size < MAX_POINTS) openEditor(-1) },
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

    // --- editor -------------------------------------------------------------
    OverlayDialog(
        show = show && pickerVisible,
        title = if (isAdding) "添加时间点" else "修改时间点",
        summary = if (isAdding) {
            "点选小时和分钟"
        } else {
            editing?.let { "${minuteLabel(it)} · 点选新的时间" } ?: "点选小时和分钟"
        },
        onDismissRequest = { closeEditor() },
    ) {
        Column {
            CurrentTimeDisplay(hour, minute)
            Row(
                modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberColumn(
                    values = (0..23).toList(),
                    selected = hour,
                    label = { "%02d 时".format(it) },
                    onSelect = { hour = it },
                    modifier = Modifier.weight(1f),
                )
                NumberColumn(
                    values = (0..59).toList(),
                    selected = minute,
                    label = { "%02d 分".format(it) },
                    onSelect = { minute = it },
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
                TextButton(text = "取消", onClick = { closeEditor() }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { commit() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (isAdding) "添加" else "保存")
                }
            }
        }
    }
}


/** Selected time shown as a large HH:MM readout. */
@Composable
private fun CurrentTimeDisplay(hour: Int, minute: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/**
 * A tappable scrolling column. Deliberately not a wheel: Miuix's NumberPicker only reports the new
 * value after the fling settles, so tapping Save mid-scroll saved the *previous* time.
 */
@Composable
private fun NumberColumn(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0),
    )
    LazyColumn(
        state = state,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.size) { index ->
            val value = values[index]
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp)
                    .background(
                        if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    style = if (isSelected) MiuixTheme.textStyles.title3 else MiuixTheme.textStyles.body1,
                    color = if (isSelected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
