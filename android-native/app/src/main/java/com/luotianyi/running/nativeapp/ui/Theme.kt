package com.luotianyi.running.nativeapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LuoBlue = Color(0xFF66CCFF)
val RunOrange = Color(0xFFFF8C42)
val AppBackground = Color(0xFF10111A)
val AppSurface = Color(0xFF1D202D)

private val Colors = darkColorScheme(
    primary = LuoBlue, onPrimary = Color(0xFF002C43),
    primaryContainer = LuoBlue.copy(alpha = .16f), onPrimaryContainer = LuoBlue,
    secondary = RunOrange, onSecondary = Color(0xFF3B1700),
    secondaryContainer = LuoBlue.copy(alpha = .16f), onSecondaryContainer = LuoBlue,
    tertiary = LuoBlue, onTertiary = Color(0xFF002C43),
    tertiaryContainer = LuoBlue.copy(alpha = .16f), onTertiaryContainer = LuoBlue,
    inversePrimary = LuoBlue, surfaceTint = LuoBlue,
    background = AppBackground, surface = AppSurface,
    surfaceContainer = AppSurface, surfaceContainerHigh = Color(0xFF282D3C),
    onBackground = Color(0xFFFFF6EC), onSurface = Color(0xFFFFF6EC),
    onSurfaceVariant = Color(0xFFA6AFBF), outline = Color(0xFF454C5D),
)

@Composable
fun LuotianyiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
