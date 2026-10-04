package com.agentos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepBlue = Color(0xFF2962FF)
private val Teal = Color(0xFF00BFA5)
private val DarkBg = Color(0xFF0F1216)
private val DarkSurface = Color(0xFF161B22)
private val LightBg = Color(0xFFF7F9FC)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9EFF),
    secondary = Teal,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1E2430),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    secondary = Color(0xFF00897B),
    background = LightBg,
    surface = Color.White,
    error = Color(0xFFC62828)
)

@Composable
fun AgentOsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
