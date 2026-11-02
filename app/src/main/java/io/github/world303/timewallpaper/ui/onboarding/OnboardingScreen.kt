package io.github.world303.timewallpaper.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.world303.timewallpaper.core.alarm.AlarmScheduler
import io.github.world303.timewallpaper.core.compat.OemCompat
import io.github.world303.timewallpaper.ui.component.AppIconBrush
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * First-run guide. Two pages: what the app does, then the three system permissions that keep the
 * schedule alive on Chinese ROMs. Everything can be skipped — the app still works, it just may be
 * delayed by aggressive battery managers.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val batteryOk = remember(resumeTick) { OemCompat.isIgnoringBatteryOptimizations(context) }
    val exactAlarmOk = remember(resumeTick) { AlarmScheduler.hasExactAlarmPermission(context) }
    val autoStartLabel = remember(resumeTick) { OemCompat.autoStartLabel(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "跳过", onClick = onFinish)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            if (page == 0) {
                WelcomePage()
            } else {
                PermissionPage(
                    batteryOk = batteryOk,
                    exactAlarmOk = exactAlarmOk,
                    autoStartLabel = autoStartLabel,
                    onAutoStart = { OemCompat.openAutoStart(context) },
                    onBattery = {
                        runCatching { context.startActivity(OemCompat.batteryOptimizationIntent(context)) }
                    },
                    onExactAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 20.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage == 0) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                } else {
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(if (pagerState.currentPage == 0) "继续" else "开始使用")
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(AppIconBrush),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = io.github.world303.timewallpaper.R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "时间壁纸",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "提前给每个时间段挑好壁纸，到点自动换。\n默认 00:00 / 06:00 / 12:00 / 18:00，也可以自己加时间点。",
            style = MiuixTheme.textStyles.paragraph,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("省电的做法", style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.height(6.dp))
                Text(
                    "没有常驻服务、没有轮询：只在每个时间点被系统唤醒一次，换完壁纸立刻睡回去。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun PermissionPage(
    batteryOk: Boolean,
    exactAlarmOk: Boolean,
    autoStartLabel: String?,
    onAutoStart: () -> Unit,
    onBattery: () -> Unit,
    onExactAlarm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "让切换稳定运行",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "国产系统的省电策略可能会拦掉定时任务。下面三项都建议开启，也可以稍后在设置里再弄。",
            style = MiuixTheme.textStyles.paragraph,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            PermissionRow(
                title = "自启动",
                summary = if (autoStartLabel != null) "打开「$autoStartLabel」允许自启动" else "在系统设置中允许自启动",
                done = false,
                actionLabel = "去开启",
                onAction = onAutoStart,
            )
            Spacer(Modifier.height(2.dp))
            PermissionRow(
                title = "忽略电池优化",
                summary = if (batteryOk) "已允许后台运行" else "避免息屏后被系统冻结",
                done = batteryOk,
                actionLabel = if (batteryOk) "已开启" else "去开启",
                onAction = onBattery,
            )
            Spacer(Modifier.height(2.dp))
            PermissionRow(
                title = "精确闹钟",
                summary = if (exactAlarmOk) "已授权" else "保证在整点准时切换（Android 12+）",
                done = exactAlarmOk,
                actionLabel = if (exactAlarmOk) "已开启" else "去开启",
                onAction = onExactAlarm,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Alarm,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "这些权限只用于按时切换壁纸，App 不联网上传任何东西。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.title4)
            Text(
                summary,
                style = MiuixTheme.textStyles.footnote2,
                color = if (done) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        TextButton(
            text = actionLabel,
            onClick = onAction,
            enabled = !done,
        )
    }
}
