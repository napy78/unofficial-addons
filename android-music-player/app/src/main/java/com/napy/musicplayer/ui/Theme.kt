package com.napy.musicplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B14)
val Surface1 = Color(0xFF17172A)
val Surface2 = Color(0xFF202039)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4081)
val Cyan = Color(0xFF18FFFF)
val TextDim = Color(0xFF9A9AB8)

val AccentBrush = Brush.linearGradient(listOf(Violet, Pink))
val BgBrush = Brush.verticalGradient(listOf(Color(0xFF241447), Bg, Color(0xFF07070D)))

@Composable
fun NapyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, secondary = Pink, tertiary = Cyan,
            background = Bg, surface = Surface1, onSurface = Color.White, onBackground = Color.White
        ),
        content = content
    )
}
