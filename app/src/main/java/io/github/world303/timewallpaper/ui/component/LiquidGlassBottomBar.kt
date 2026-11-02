package io.github.world303.timewallpaper.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Floating bottom bar with the Liquid Glass effect (Kyant0/AndroidLiquidGlass, published as
 * `io.github.kyant0:backdrop`). Effect parameters are the library's own defaults: vibrancy +
 * blur(8dp) + lens(24dp, 24dp), the exact stack used by the library's LiquidBottomTabs sample.
 */
@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop,
    selectedTab: Int,
    items: List<Pair<ImageVector, String>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = !isSystemInDarkTheme()
    val containerColor = if (isLight) {
        Color(0xFFFAFAFA).copy(alpha = 0.4f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val itemWidth = 62.dp
    Row(
        modifier = modifier
            .height(60.dp)
            .width(itemWidth * items.size + 12.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(containerColor) },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, label) ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
