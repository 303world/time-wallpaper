package io.github.world303.timewallpaper.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.world303.timewallpaper.BuildConfig
import io.github.world303.timewallpaper.core.AppGraph
import io.github.world303.timewallpaper.core.alarm.AlarmScheduler
import io.github.world303.timewallpaper.core.compat.OemCompat
import io.github.world303.timewallpaper.core.model.MAX_POINTS
import io.github.world303.timewallpaper.core.model.WallpaperSlot
import io.github.world303.timewallpaper.core.model.minuteLabel
import io.github.world303.timewallpaper.core.update.UpdateChecker
import io.github.world303.timewallpaper.core.wallpaper.WallpaperApplier
import io.github.world303.timewallpaper.core.wallpaper.WallpaperStore
import io.github.world303.timewallpaper.ui.component.liquid.LiquidBottomTab
import io.github.world303.timewallpaper.ui.component.liquid.LiquidBottomTabs
import io.github.world303.timewallpaper.ui.crop.CropScreen
import io.github.world303.timewallpaper.ui.home.HomeScreen
import io.github.world303.timewallpaper.ui.home.WallpaperTarget
import io.github.world303.timewallpaper.ui.media.MediaPickerSheet
import io.github.world303.timewallpaper.ui.onboarding.OnboardingScreen
import io.github.world303.timewallpaper.ui.settings.AboutScreen
import io.github.world303.timewallpaper.ui.settings.PointManagerDialog
import io.github.world303.timewallpaper.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = AppGraph.repo
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backdrop = rememberLayerBackdrop()
    val pagerState = rememberPagerState(pageCount = { 2 })

    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var onboarded by rememberSaveable { mutableStateOf(state.onboarded) }
    var showPoints by remember { mutableStateOf(false) }
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }
    var pickerTarget by remember { mutableStateOf(WallpaperTarget.HOME) }
    var target by rememberSaveable { mutableStateOf(WallpaperTarget.HOME) }
    var cropUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cropSlot by rememberSaveable { mutableIntStateOf(0) }
    var cropTarget by rememberSaveable { mutableStateOf(WallpaperTarget.HOME) }
    var isApplying by remember { mutableStateOf(false) }
    var recentTick by remember { mutableIntStateOf(0) }

    val screenSize = remember { WallpaperApplier.targetSize(context) }
    val recentFiles = remember(pickerForSlot, recentTick, state.hiddenRecentIds) {
        if (pickerForSlot != null) {
            WallpaperStore.list(context).filter { it.name !in state.hiddenRecentIds }
        } else {
            emptyList()
        }
    }
    // The bar stays visible; modals are hosted at the root, above it (see MiuixPopupHost below),
    // so there is no need to tear the bar down when a sheet opens.
    val chromeVisible = !aboutOpen

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun startCrop(uri: String, slotStart: Int, cropFor: WallpaperTarget) {
        cropUri = uri
        cropSlot = slotStart
        cropTarget = cropFor
        pickerForSlot = null
    }

    fun toggleAuto(enabled: Boolean) {
        repo.update { it.copy(autoSwitch = enabled) }
        if (enabled) {
            AlarmScheduler.scheduleNext(context)
        } else {
            AlarmScheduler.cancel(context)
        }
    }

    fun changeSeparateLock(enabled: Boolean) {
        repo.update { it.copy(separateLock = enabled) }
        scope.launch {
            if (!enabled) {
                WallpaperApplier.clearLockWallpaper(context)
            }
            WallpaperApplier.applyCurrent(context, force = true)
        }

    }

    fun addPoint(minute: Int) {
        val current = repo.state.value
        when {
            current.slots.any { it.startMinute == minute } -> toast("这个时间点已经存在")
            current.slots.size >= MAX_POINTS -> toast("最多只能有 24 个时间点")
            else -> {
                repo.update { s ->
                    s.copy(slots = (s.slots + WallpaperSlot(minute)).sortedBy { it.startMinute })
                }
                AlarmScheduler.scheduleNext(context)
            }
        }
    }

    fun updatePoint(old: Int, new: Int) {
        val current = repo.state.value
        if (new != old && current.slots.any { it.startMinute == new }) {
            toast("这个时间点已经存在")
            return
        }
        repo.update { s ->
            s.copy(slots = s.slots.map { if (it.startMinute == old) it.copy(startMinute = new) else it }
                .sortedBy { it.startMinute })
        }
        AlarmScheduler.scheduleNext(context)
    }

    fun removePoint(minute: Int) {
        val current = repo.state.value
        if (current.slots.size <= 1) {
            toast("至少要保留一个时间点")
            return
        }
        repo.update { s -> s.copy(slots = s.slots.filterNot { it.startMinute == minute }) }
        AlarmScheduler.scheduleNext(context)
    }

    fun deleteRecent(imageId: String) {
        val snapshot = repo.state.value
        val inUse = snapshot.slots.any { it.homeImageId == imageId || it.lockImageId == imageId }
        if (inUse) {
            // A wallpaper that is currently assigned to a slot must never disappear from under it.
            // Just hide it from the recent list; the file stays until the slot lets go of it.
            repo.update { it.copy(hiddenRecentIds = (it.hiddenRecentIds + imageId).distinct()) }
        } else {
            WallpaperStore.delete(context, imageId)
            repo.update { it.copy(hiddenRecentIds = it.hiddenRecentIds - imageId) }
        }
        recentTick++
    }

    // --- pickers -------------------------------------------------------------
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val slot = pickerForSlot
        if (uri != null && slot != null) {
            startCrop(uri.toString(), slot, pickerTarget)
        } else if (uri == null) {
            pickerForSlot = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val slot = pickerForSlot
        if (uri != null && slot != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startCrop(uri.toString(), slot, pickerTarget)
        } else if (uri == null) {
            pickerForSlot = null
        }
    }

    // --- automatic update check ---------------------------------------------
    LaunchedEffect(Unit) {
        if (!state.autoCheckUpdate) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - state.lastUpdateCheckAt < 24L * 60 * 60 * 1000) return@LaunchedEffect
        repo.update { it.copy(lastUpdateCheckAt = now) }
        val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
        result.onSuccess { info ->
            if (UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                toast("发现新版本 v${UpdateChecker.normalizeVersion(info.version)}，可在「设置 → 关于」更新")
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { WallpaperApplier.applyCurrent(context, force = false) }
        // Housekeeping: drop old unreferenced crops and stale hidden ids.
        WallpaperStore.prune(context, repo.state.value)
        repo.update { it.copy(hiddenRecentIds = it.hiddenRecentIds.filter { id -> id in WallpaperStore.liveIds(context) }) }
    }

    if (!onboarded) {
        OnboardingScreen(
            onFinish = {
                repo.update { it.copy(onboarded = true) }
                onboarded = true
                AlarmScheduler.scheduleNext(context)
            },
        )
        return
    }

    val pendingUri = cropUri
    if (pendingUri != null) {
        BackHandler { cropUri = null }
        CropScreen(
            sourceUri = pendingUri.toUri(),
            targetWidth = screenSize.first,
            targetHeight = screenSize.second,
            title = if (cropTarget == WallpaperTarget.LOCK) "裁剪锁屏壁纸" else "裁剪桌面壁纸",
            onCancel = { cropUri = null },
            onSaved = { imageId ->
                repo.update { current ->
                    current.copy(
                        slots = current.slots.map {
                            if (it.startMinute != cropSlot) it
                            else if (cropTarget == WallpaperTarget.LOCK) it.copy(lockImageId = imageId)
                            else it.copy(homeImageId = imageId)
                        },
                    )
                }
                cropUri = null
                recentTick++
                WallpaperStore.prune(context, repo.state.value)
                AlarmScheduler.scheduleNext(context)
                scope.launch { WallpaperApplier.applyCurrent(context, force = true) }
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AnimatedContent(
                targetState = aboutOpen,
                transitionSpec = {
                    if (targetState) {
                        slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } togetherWith
                            fadeOut(tween(160))
                    } else {
                        fadeIn(tween(160)) togetherWith
                            slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
                    }
                },
                label = "about",
            ) { about ->
                if (about) {
                    BackHandler { aboutOpen = false }
                    AboutScreen(
                        state = state,
                        onBack = { aboutOpen = false },
                        onAutoCheckChange = { enabled -> repo.update { it.copy(autoCheckUpdate = enabled) } },
                        snackbarHostState = snackbar,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        if (page == 0) {
                            HomeScreen(
                                state = state,
                                screenSize = screenSize,
                                isApplying = isApplying,
                                target = target,
                                onTargetChange = { target = it },
                                onToggleAuto = ::toggleAuto,
                                onApplyNow = {
                                    val snapshot = repo.state.value
                                    if (!snapshot.isComplete()) {
                                        toast("还有 ${snapshot.homeMissingCount() + snapshot.lockMissingCount()} 个时间段没有设置壁纸，先补全再应用")
                                    } else if (!isApplying) {
                                        isApplying = true
                                        scope.launch {
                                            val result = WallpaperApplier.applyCurrent(context, force = true)
                                            isApplying = false
                                            result.message?.let { toast(it) }
                                        }
                                    }
                                },
                                onSlotClick = { slot, slotTarget ->
                                    pickerTarget = slotTarget
                                    pickerForSlot = slot.startMinute
                                },
                                overlay = {
                                    val pickerSlot = pickerForSlot
                                    if (pickerSlot != null) {
                                        MediaPickerSheet(
                                            show = true,
                                            recent = recentFiles,
                                            onDismiss = { pickerForSlot = null },
                                            onPickGallery = {
                                                galleryPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                )
                                            },
                                            onPickFile = { filePicker.launch(arrayOf("image/*")) },
                                            onPickRecent = { imageId ->
                                                repo.update { current ->
                                                    current.copy(
                                                        slots = current.slots.map {
                                                            if (it.startMinute != pickerSlot) it
                                                            else if (pickerTarget == WallpaperTarget.LOCK) it.copy(lockImageId = imageId)
                                                            else it.copy(homeImageId = imageId)
                                                        },
                                                    )
                                                }
                                                pickerForSlot = null
                                                scope.launch { WallpaperApplier.applyCurrent(context, force = false) }
                                            },
                                            onDeleteRecent = ::deleteRecent,
                                        )
                                    }
                                },
                            )
                        } else {
                            SettingsScreen(
                                state = state,
                                onToggleSeparateLock = ::changeSeparateLock,
                                onManagePoints = { showPoints = true },
                                onAbout = { aboutOpen = true },
                                onRequestBattery = {
                                    runCatching { context.startActivity(OemCompat.batteryOptimizationIntent(context)) }
                                        .onFailure { toast("无法打开电池优化设置") }
                                },
                                onOpenAutoStart = {
                                    if (!OemCompat.openAutoStart(context)) {
                                        toast("无法打开自启动设置，请到系统设置中手动允许")
                                    }
                                },
                                onRequestExactAlarm = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                    .setData("package:${context.packageName}".toUri())
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }.onFailure { toast("无法打开精确闹钟设置") }
                                    } else {
                                        toast("当前系统无需额外授权")
                                    }
                                },
                                overlay = {
                                    PointManagerDialog(
                                        show = showPoints,
                                        points = state.sortedSlots().map { it.startMinute },
                                        onDismiss = { showPoints = false },
                                        onAdd = ::addPoint,
                                        onUpdate = ::updatePoint,
                                        onRemove = ::removePoint,
                                    )
                                },
                            )
                        }
                    }
                    BackHandler(enabled = pagerState.currentPage == 1) {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                    BackHandler(enabled = pagerState.currentPage == 0) {
                        (context as? Activity)?.finish()
                    }
                }
            }
        }

        SnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 124.dp),
        )

        // NOTE: conditionally composing (not AnimatedVisibility) keeps the liquid-glass backdrop
        // sampling sane across modal open/close; Visibility wrappers left a dead bar behind.
        if (chromeVisible) {
            LiquidBottomTabs(
                selectedTabIndex = { pagerState.currentPage },
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                backdrop = backdrop,
                tabsCount = 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .width(150.dp)
                    .height(56.dp),
            ) {
                repeat(2) { index ->
                    LiquidBottomTab(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    ) {
                        Icon(
                            imageVector = if (index == 0) MiuixIcons.Home else MiuixIcons.Settings,
                            contentDescription = if (index == 0) "主页" else "设置",
                            modifier = Modifier.size(28.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Single root host for every Miuix dialog / bottom sheet. Pages no longer own a Scaffold,
        // so without this they would register into the default state list with nobody to draw it.
        MiuixPopupUtils.MiuixPopupHost()
    }
}
