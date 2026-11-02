package io.github.world303.timewallpaper.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Extra band below the title bar where the progressive blur fades out to nothing. */
private val BarFadeExtent = 36.dp

/**
 * Backdrop that samples the scrolling page, used by [GlassTopAppBar]. Returns null when the
 * runtime shader is unavailable (very old devices); the bar then falls back to a flat tint.
 */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/**
 * Top bar with a *progressive* frosted glass (miuix `progressiveTextureBlur`): full blur at the
 * status bar, fading smoothly to clear at the bottom — no hard edge like a plain blur rectangle.
 * The page content has to be drawn with `Modifier.layerBackdrop(backdrop)` so it can scroll
 * underneath the bar and be sampled.
 */
@Composable
fun GlassTopAppBar(
    title: String,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val surface = MiuixTheme.colorScheme.surface
    val blurModifier = if (backdrop != null) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = ProgressiveBlur.Top,
            colors = BlurDefaults.blurColors(),
        )
    } else {
        Modifier.background(surface.copy(alpha = 0.92f))
    }
    Column(modifier = modifier.fillMaxWidth().then(blurModifier)) {
        SmallTopAppBar(
            title = title,
            color = Color.Transparent,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        // Extra frosted band so the fade has room to disappear instead of ending on a line.
        Spacer(Modifier.height(BarFadeExtent))
    }
}

/** Height occupied by [GlassTopAppBar], status bar included. */
@Composable
fun glassTopBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 50.dp + BarFadeExtent
