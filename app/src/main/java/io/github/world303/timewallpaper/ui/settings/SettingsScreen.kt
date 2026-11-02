package io.github.world303.timewallpaper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.world303.timewallpaper.core.alarm.AlarmScheduler
import io.github.world303.timewallpaper.core.compat.OemCompat
import io.github.world303.timewallpaper.core.model.AppState
import io.github.world303.timewallpaper.core.model.minuteLabel
import io.github.world303.timewallpaper.core.wallpaper.WallpaperStore
import top.yukonga.miuix.kmp.blur.layerBackdrop
import io.github.world303.timewallpaper.ui.component.GlassTopAppBar
import io.github.world303.timewallpaper.ui.component.glassTopBarHeight
import io.github.world303.timewallpaper.ui.component.rememberGlassBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    state: AppState,
    onToggleSeparateLock: (Boolean) -> Unit,
    onManagePoints: () -> Unit,
    onAbout: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenAutoStart: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    overlay: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val batteryOk = remember(resumeTick) { OemCompat.isIgnoringBatteryOptimizations(context) }
    val exactAlarmOk = remember(resumeTick) { AlarmScheduler.hasExactAlarmPermission(context) }
    val autoStartLabel = remember(resumeTick) { OemCompat.autoStartLabel(context) }
    val storage = remember(resumeTick, state.slots) { WallpaperStore.usedBytes(context) }
    val wallCount = remember(resumeTick, state.slots) { WallpaperStore.list(context).size }
    val points = state.sortedSlots().map { it.startMinute }

    val pageBackdrop = rememberGlassBackdrop()
    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(
                if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
            ),
            contentPadding = PaddingValues(
                top = glassTopBarHeight() + 8.dp,
                bottom = 120.dp,
            ),
        ) {
            item { SmallTitle("自动化") }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BasicComponent(
                        title = "锁屏与桌面分开设置",
                        summary = if (state.separateLock) {
                            "已开启：主页可分别设置桌面和锁屏壁纸"
                        } else {
                            "锁屏跟随桌面壁纸"
                        },
                        endActions = {
                            Switch(checked = state.separateLock, onCheckedChange = onToggleSeparateLock)
                        },
                        onClick = { onToggleSeparateLock(!state.separateLock) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "切换时间点",
                        summary = "${points.size} 个 · ${points.joinToString("、") { minuteLabel(it) }}",
                        onClick = onManagePoints,
                    )
                }
            }

            item { SmallTitle("后台运行") }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BasicComponent(
                        title = "忽略电池优化",
                        summary = if (batteryOk) "已允许后台运行" else "未允许，息屏后可能被限制",
                        onClick = onRequestBattery,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "自启动",
                        summary = if (autoStartLabel != null) "前往「$autoStartLabel」开启" else "前往系统设置允许自启动",
                        onClick = onOpenAutoStart,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "精确闹钟",
                        summary = if (exactAlarmOk) "已授权，切换时间准确" else "未授权，切换可能被延后",
                        onClick = onRequestExactAlarm,
                    )
                }
            }
            item { SmallTitle("其他") }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BasicComponent(
                        title = "已保存的壁纸",
                        summary = "$wallCount 张 · ${"%.1f".format(storage / 1024.0 / 1024.0)} MB",
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "关于",
                        summary = "版本、开源协议与更新",
                        onClick = onAbout,
                    )
                }
            }
        }
        overlay()
        GlassTopAppBar(title = "设置", backdrop = pageBackdrop)
    }
}
