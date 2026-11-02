package io.github.world303.timewallpaper.ui.component.liquid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Liquid-glass bottom pill with a draggable selection capsule.
 *
 * Structure (bottom to top): frosted capsule -> sliding liquid capsule -> icons.
 * The selector deliberately has no specular highlight: it is a refractive (lens) glass pill, so it
 * reads as a piece of liquid sliding inside the bar rather than a raised button.
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.55f)
    } else {
        Color(0xFF1C1C1C).copy(alpha = 0.6f)
    }
    val selectorColor = if (isLightTheme) Color(0xFFE2E2E2).copy(alpha = 0.75f) else Color(0xFF3C3C3C).copy(alpha = 0.75f)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount }
        val tabWidth = with(density) { tabWidthPx.toDp() }

        val selected = selectedTabIndex().coerceIn(0, tabsCount - 1)
        val animationScope = rememberCoroutineScope()
        val drag = remember(animationScope, tabWidthPx, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(target.toFloat())
                    onTabSelected(target)
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                },
            )
        }

        // Keep the capsule in sync when the page is changed by a tap or by the pager.
        LaunchedEffect(selected) {
            if (abs(drag.value - selected) > 0.01f) drag.animateToValue(selected.toFloat())
        }

        // 1) frosted capsule
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(containerColor) },
                ),
        )

        // 2) sliding liquid capsule (refraction only, no highlight, no shadow)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .offset {
                    IntOffset(
                        x = (4.dp.toPx() + drag.value * tabWidthPx).roundToInt(),
                        y = 0,
                    )
                }
                .width(tabWidth)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val pressed = drag.pressProgress
                        lens(
                            refractionHeight = 16f.dp.toPx() * (0.55f + 0.45f * pressed),
                            refractionAmount = 22f.dp.toPx() * (0.55f + 0.45f * pressed),
                            chromaticAberration = true,
                        )
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { drawRect(selectorColor) },
                ),
        )

        // 3) icons on top; the row also owns the horizontal drag gesture.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .then(drag.modifier),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
