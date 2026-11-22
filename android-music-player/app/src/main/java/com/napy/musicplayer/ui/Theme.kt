package com.napy.musicplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette ad alto contrasto: sfondo quasi nero, card nettamente piu' chiare con bordo, testo bianco/lilla chiaro.
val Bg = Color(0xFF05050B)
val Surface1 = Color(0xFF1E1E38)
val Surface2 = Color(0xFF2C2C52)
val Border = Color(0xFF5A5A92)
val Violet = Color(0xFF9A78FF)
val Pink = Color(0xFFFF7AA8)
val Cyan = Color(0xFF55EBFF)
val TextDim = Color(0xFFD0D0EA)

val AccentBrush = Brush.linearGradient(listOf(Color(0xFF6B3CF2), Color(0xFFE0307A)))
val BgBrush = Brush.verticalGradient(listOf(Color(0xFF2A1758), Bg, Color(0xFF040408)))

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
