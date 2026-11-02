package io.github.world303.timewallpaper.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/** Thumbnail for a cropped wallpaper file, with a tasteful placeholder when empty. */
@Composable
fun WallpaperThumb(
    file: File?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    emptyText: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier.clip(shape).background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null && file.isFile) {
            AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = MiuixIcons.Image,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (emptyText != null) {
                    Text(
                        text = emptyText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Gradient used behind the "current wallpaper" hero card when no wallpaper is set yet. */
val HeroPlaceholderBrush: Brush = Brush.linearGradient(
    listOf(Color(0xFF2A2A2A), Color(0xFF141414), Color(0xFF000000)),
)

/** Same gradient as the launcher icon background, so app-icon tiles stay consistent. */
val AppIconBrush: Brush = Brush.linearGradient(
    listOf(Color(0xFF5B7CFA), Color(0xFF6E62E5), Color(0xFF9A5BD6)),
)

/** Page scaffold with a Miuix small top bar and an optional back arrow. */
@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { padding -> content(padding) }
}
