package io.github.world303.timewallpaper.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.world303.timewallpaper.core.ScheduleEngine
import io.github.world303.timewallpaper.core.model.AppState
import io.github.world303.timewallpaper.core.model.WallpaperSlot
import io.github.world303.timewallpaper.core.model.slotRangeLabel
import io.github.world303.timewallpaper.core.wallpaper.WallpaperApplier
import io.github.world303.timewallpaper.core.wallpaper.WallpaperStore
import top.yukonga.miuix.kmp.blur.layerBackdrop
import io.github.world303.timewallpaper.ui.component.GlassTopAppBar
import io.github.world303.timewallpaper.ui.component.HeroPlaceholderBrush
import io.github.world303.timewallpaper.ui.component.glassTopBarHeight
import io.github.world303.timewallpaper.ui.component.rememberGlassBackdrop
import io.github.world303.timewallpaper.ui.component.WallpaperThumb
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Which wallpaper a slot edit targets. */
enum class WallpaperTarget { HOME, LOCK }

@Composable
fun rememberMinuteOfDay(): Int {
    var minute by remember { mutableIntStateOf(WallpaperApplier.currentMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            minute = WallpaperApplier.currentMinute()
        }
    }
    return minute
}

@Composable
fun HomeScreen(
    state: AppState,
    screenSize: Pair<Int, Int>,
    isApplying: Boolean,
    target: WallpaperTarget,
    onTargetChange: (WallpaperTarget) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onApplyNow: () -> Unit,
    onSlotClick: (WallpaperSlot, WallpaperTarget) -> Unit,
    overlay: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val slots = state.sortedSlots()
    val minute = rememberMinuteOfDay()
    val currentSlot = ScheduleEngine.slotAt(slots, minute)
    val nextLabel = remember(slots, state.autoSwitch) {
        if (state.autoSwitch) ScheduleEngine.nextSwitchLabel(slots, System.currentTimeMillis()) else "自动切换已关闭"
    }
    val missingHome = state.homeMissingCount()
    val missingLock = state.lockMissingCount()

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
            item(key = "hero") {
                HeroCard(
                    state = state,
                    currentSlot = currentSlot,
                    nextLabel = nextLabel,
                    isApplying = isApplying,
                    target = target,
                    onTargetChange = onTargetChange,
                    onToggleAuto = onToggleAuto,
                    onApplyNow = onApplyNow,
                    missingHome = missingHome,
                    missingLock = missingLock,
                )
            }
            item(key = "title") {
                SmallTitle(
                    text = if (state.separateLock) {
                        if (target == WallpaperTarget.HOME) "桌面壁纸" else "锁屏壁纸"
                    } else {
                        "时间段"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "slots") {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column {
                        slots.forEachIndexed { index, slot ->
                            SlotRow(
                                state = state,
                                slot = slot,
                                target = target,
                                isCurrent = slot.startMinute == currentSlot?.startMinute,
                                screenSize = screenSize,
                                onClick = { onSlotClick(slot, target) },
                            )
                            if (index != slots.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
        overlay()
        GlassTopAppBar(title = "时间壁纸", backdrop = pageBackdrop)
    }
}

@Composable
private fun HeroCard(
    state: AppState,
    currentSlot: WallpaperSlot?,
    nextLabel: String,
    isApplying: Boolean,
    target: WallpaperTarget,
    onTargetChange: (WallpaperTarget) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onApplyNow: () -> Unit,
    missingHome: Int,
    missingLock: Int,
) {
    val context = LocalContext.current
    val imageId = when (target) {
        WallpaperTarget.HOME -> currentSlot?.homeImageId
        WallpaperTarget.LOCK -> currentSlot?.lockImageId
    }
    val file = WallpaperStore.fileFor(context, imageId)
    val missing = if (target == WallpaperTarget.HOME) missingHome else missingLock
    val canApply = if (target == WallpaperTarget.HOME) missingHome == 0 else missingLock == 0

    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))),
            )
            if (state.separateLock) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x55000000))
                        .padding(3.dp),
                ) {
                    TargetChip(
                        text = "锁屏",
                        selected = target == WallpaperTarget.LOCK,
                        onClick = { onTargetChange(WallpaperTarget.LOCK) },
                    )
                    TargetChip(
                        text = "桌面",
                        selected = target == WallpaperTarget.HOME,
                        onClick = { onTargetChange(WallpaperTarget.HOME) },
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Text(
                    text = if (file != null) {
                        if (target == WallpaperTarget.HOME || !state.separateLock) "当前桌面壁纸" else "当前锁屏壁纸"
                    } else {
                        "当前时段还没有壁纸"
                    },
                    color = Color.White,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Text(
                    text = currentSlot?.let {
                        slotRangeLabel(it.startMinute, ScheduleEngine.endMinuteOf(state.slots, it))
                    } ?: "—",
                    color = Color.White,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = nextLabel,
                    color = Color(0xCCFFFFFF),
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
            if (isApplying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("设置中…", color = Color.White, style = MiuixTheme.textStyles.footnote2)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动切换", style = MiuixTheme.textStyles.title4)
                Text(
                    text = if (state.autoSwitch) "按时间段自动更换壁纸" else "已暂停，不会自动更换",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(checked = state.autoSwitch, onCheckedChange = onToggleAuto)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (missing > 0) {
                Text(
                    text = if (target == WallpaperTarget.HOME && state.separateLock) {
                        "还有 $missing 个时间段没有设置桌面壁纸"
                    } else if (target == WallpaperTarget.LOCK) {
                        "还有 $missing 个时间段没有设置锁屏壁纸"
                    } else {
                        "还有 $missing 个时间段没有设置壁纸"
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = onApplyNow,
                enabled = !isApplying && canApply,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    when {
                        isApplying -> "正在设置…"
                        !canApply -> "请先补全所有时间段"
                        else -> "设为壁纸"
                    },
                )
            }
        }
    }
}

@Composable
private fun TargetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) Color(0xFF202020) else Color.White,
        )
    }
}

@Composable
private fun SlotRow(
    state: AppState,
    slot: WallpaperSlot,
    target: WallpaperTarget,
    isCurrent: Boolean,
    screenSize: Pair<Int, Int>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageId = if (target == WallpaperTarget.HOME) slot.homeImageId else slot.lockImageId
    val file = WallpaperStore.fileFor(context, imageId)
    val end = ScheduleEngine.endMinuteOf(state.slots, slot)
    val ratio = if (screenSize.first > 0) screenSize.second.toFloat() / screenSize.first else 2f
    val thumbHeight = 92.dp
    val thumbWidth = (thumbHeight.value / ratio).dp
    Box(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = slotRangeLabel(slot.startMinute, end),
                        style = MiuixTheme.textStyles.title4,
                        color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "当前",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (file == null) "点击设置壁纸" else "已设置 · 点击更换",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (file == null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            WallpaperThumb(
                file = file,
                modifier = Modifier.size(width = thumbWidth.coerceAtLeast(38.dp), height = thumbHeight),
                cornerRadius = 10.dp,
                emptyText = null,
            )
        }
    }
}
