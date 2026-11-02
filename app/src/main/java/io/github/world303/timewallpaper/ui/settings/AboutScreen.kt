package io.github.world303.timewallpaper.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.world303.timewallpaper.BuildConfig
import io.github.world303.timewallpaper.R
import io.github.world303.timewallpaper.core.model.AppState
import io.github.world303.timewallpaper.core.update.UpdateChecker
import io.github.world303.timewallpaper.core.update.UpdateInstaller
import io.github.world303.timewallpaper.ui.component.AppIconBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ChangelogEntry(val version: String, val date: String, val items: List<String>)

private val CHANGELOG = listOf(
    ChangelogEntry(
        version = "1.0.0",
        date = "2026-11-02",
        items = listOf(
            "按时间段自动切换壁纸，默认 00:00 / 06:00 / 12:00 / 18:00",
            "支持自行增删、修改时间点（最多 24 个）",
            "桌面与锁屏可分开设置，默认锁屏跟随桌面",
            "内置裁剪：双指缩放、拖动、90° 旋转，输出屏幕分辨率",
            "液态玻璃底栏、解锁自动校正、后台省电切换",
        ),
    ),
)

private val LogoSpacerHeight = 320.dp

/**
 * About page, laid out after Nexio 课程表's about screen: full-bleed gradient, big app icon and
 * name that fade away as you scroll, and glassy rounded cards for links / updates / changelog.
 */
@Composable
fun AboutScreen(
    state: AppState,
    onBack: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var versionNote by remember { mutableStateOf<String?>(null) }
    var expandedLog by remember { mutableStateOf(0) }

    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val background = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF1B1226), Color(0xFF171326), Color(0xFF0E0D16)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF7E9FF), Color(0xFFEDE6FF), Color(0xFFFDF1F8)))
    }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("无法打开链接") }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        versionNote = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            checking = false
            result.onSuccess { info ->
                if (UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    release = info
                    showUpdate = true
                } else {
                    versionNote = "已是最新版本"
                    toast("已是最新版本 v${BuildConfig.VERSION_NAME}")
                }
            }.onFailure {
                versionNote = "检查更新失败"
                toast("检查更新失败：${it.message ?: "网络错误"}")
            }
        }
    }

    fun downloadAndInstall(info: UpdateChecker.ReleaseInfo) {
        val url = info.apkUrl ?: run {
            toast("该版本没有提供 APK 下载")
            return
        }
        downloading = true
        progress = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                UpdateInstaller.download(context, url) { p -> progress = p }
            }
            downloading = false
            result.onSuccess { file ->
                if (!UpdateInstaller.canInstall(context)) {
                    toast("请允许安装未知来源应用后重试")
                    UpdateInstaller.requestInstallPermission(context)
                } else {
                    UpdateInstaller.install(context, file)
                    showUpdate = false
                }
            }.onFailure { toast("下载失败：${it.message ?: "网络错误"}") }
        }
    }

    val logoHeightPx = with(LocalDensity.current) { LogoSpacerHeight.toPx() }
    val scrollProgress by remember(logoHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / logoHeightPx).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = LogoSpacerHeight + 40.dp,
                bottom = 40.dp,
            ),
        ) {
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        ArrowRow("GitHub 仓库", UpdateChecker.REPO) {
                            openUrl("https://github.com/${UpdateChecker.REPO}")
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ArrowRow("开源协议", "GNU General Public License v3.0") {
                            openUrl("https://www.gnu.org/licenses/gpl-3.0.html")
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ArrowRow("界面组件", "Miuix · Liquid Glass") {
                            openUrl("https://github.com/compose-miuix-ui/miuix")
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        BasicComponent(
                            title = "检查更新",
                            summary = when {
                                checking -> "正在检查…"
                                versionNote != null -> versionNote
                                else -> "从 GitHub Releases 获取最新版本"
                            },
                            onClick = { checkUpdate() },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        BasicComponent(
                            title = "自动检查更新",
                            summary = "启动时检查一次，24 小时内不重复",
                            endActions = {
                                Switch(checked = state.autoCheckUpdate, onCheckedChange = onAutoCheckChange)
                            },
                            onClick = { onAutoCheckChange(!state.autoCheckUpdate) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "更新日志",
                    style = MiuixTheme.textStyles.title4,
                    modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(CHANGELOG.size) { index ->
                val entry = CHANGELOG[index]
                Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("v${entry.version}", style = MiuixTheme.textStyles.title4)
                                Text(
                                    entry.date,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(onClick = { expandedLog = if (expandedLog == index) -1 else index }) {
                                Icon(
                                    imageVector = if (expandedLog == index) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        if (expandedLog == index) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                entry.items.forEach { item ->
                                    Text(
                                        text = "· $item",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "完全本地运行，不上传任何图片。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Logo block: floats above the list and fades out as the user scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 40.dp)
                .graphicsLayer {
                    alpha = 1f - ((scrollProgress - 0.55f) / 0.35f).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(26.dp)).background(AppIconBrush),
                contentAlignment = Alignment.Center,
            ) {
                val iconPainter: Painter = painterResource(id = R.drawable.ic_launcher_foreground)
                Image(painter = iconPainter, contentDescription = null, modifier = Modifier.size(84.dp))
            }
            Text(
                text = "时间壁纸",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 4.dp),
        ) {
            Icon(MiuixIcons.Back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
        }
    }

    OverlayDialog(
        show = showUpdate,
        title = "发现新版本",
        summary = release?.version?.let { "v${UpdateChecker.normalizeVersion(it)}" },
        onDismissRequest = { if (!downloading) showUpdate = false },
    ) {
        Column {
            val notes = release?.notes.orEmpty().trim()
            if (notes.isNotEmpty()) {
                Text(
                    text = notes.take(600),
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (release?.apkUrl == null) {
                Text(
                    "该版本未附带 APK，请前往 GitHub 下载。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (downloading) {
                Text(
                    "正在下载… $progress%",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "稍后",
                    onClick = { showUpdate = false },
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                )
                Button(
                    onClick = {
                        val info = release
                        if (info != null) downloadAndInstall(info) else release?.pageUrl?.let { openUrl(it) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (downloading) "下载中" else "立即更新")
                }
            }
        }
    }
}

@Composable
private fun ArrowRow(title: String, summary: String, onClick: () -> Unit) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
        endActions = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
