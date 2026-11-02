package io.github.world303.timewallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Indigo accent, a nod to the "time" theme of the app icon. */
private val Accent = Color(0xFF5B6CF0)
private val AccentDark = Color(0xFF7C8BFF)

@Composable
fun TimeWallpaperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = AccentDark,
            primaryVariant = AccentDark,
            primaryContainer = Color(0xFF6E7BF3),
            onPrimaryVariant = Color(0xFFD6DAFF),
        )
    } else {
        lightColorScheme(
            primary = Accent,
            primaryVariant = Accent,
            primaryContainer = Accent,
            onPrimaryVariant = Color(0xFFD6DAFF),
        )
    }
    MiuixTheme(colors = colors, content = content)
}
