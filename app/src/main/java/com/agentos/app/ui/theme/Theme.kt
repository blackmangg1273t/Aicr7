package com.agentos.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/* ------------------------------------------------------------------ */
/* AgentOS premium palette — deep dark layers, restrained accents.      */
/* Philosophy: dark layered surfaces carry the UI; the accent is used   */
/* only where attention is needed (running state, primary actions).     */
/* ------------------------------------------------------------------ */

object OsColors {
    // Layers (dark)
    val Bg = Color(0xFF0A0C12)             // app background — deep, not pure black
    val Surface = Color(0xFF10141C)        // base surface
    val SurfaceHigh = Color(0xFF161B26)    // elevated surface (cards)
    val SurfaceInteractive = Color(0xFF1C2333) // interactive surfaces (chips, fields)
    val SurfaceAccent = Color(0xFF1A2138)  // accent-tinted surface

    // Borders
    val Border = Color(0xFF242C40)
    val BorderStrong = Color(0xFF32405E)

    // Accent — electric blue / violet, used sparingly
    val Accent = Color(0xFF7C9EFF)
    val AccentViolet = Color(0xFFA78BFA)
    val AccentDim = Color(0xFF5468A8)

    // Text
    val TextPrimary = Color(0xFFE9EDF5)
    val TextSecondary = Color(0xFF9AA3B8)
    val TextMuted = Color(0xFF5E6880)

    // Semantic
    val Success = Color(0xFF5EE29A)
    val SuccessContainer = Color(0xFF12291D)
    val Error = Color(0xFFFF7A85)
    val ErrorContainer = Color(0xFF331A1E)
    val Warning = Color(0xFFFFC46B)
    val WarningContainer = Color(0xFF332814)

    // Special surfaces
    val CodeBg = Color(0xFF0B0E15)          // code blocks / raw viewers
    val CodeBgHeader = Color(0xFF121623)    // code block header strip

    val AccentGradient = Brush.linearGradient(listOf(Accent, AccentViolet))

    /** Ambient light: a soft radial bloom used behind key screens (cheap, no blur). */
    val AmbientGlow = Brush.radialGradient(
        colors = listOf(Color(0x147C9EFF), Color(0x00) ),
        radius = 900f
    )
}

/** Extended colors exposed alongside MaterialTheme.colorScheme. */
@Immutable
data class AgentOsColors(
    val bg: Color = OsColors.Bg,
    val surface: Color = OsColors.Surface,
    val surfaceHigh: Color = OsColors.SurfaceHigh,
    val surfaceInteractive: Color = OsColors.SurfaceInteractive,
    val surfaceAccent: Color = OsColors.SurfaceAccent,
    val border: Color = OsColors.Border,
    val borderStrong: Color = OsColors.BorderStrong,
    val accent: Color = OsColors.Accent,
    val accentViolet: Color = OsColors.AccentViolet,
    val accentDim: Color = OsColors.AccentDim,
    val textPrimary: Color = OsColors.TextPrimary,
    val textSecondary: Color = OsColors.TextSecondary,
    val textMuted: Color = OsColors.TextMuted,
    val success: Color = OsColors.Success,
    val successContainer: Color = OsColors.SuccessContainer,
    val error: Color = OsColors.Error,
    val errorContainer: Color = OsColors.ErrorContainer,
    val warning: Color = OsColors.Warning,
    val warningContainer: Color = OsColors.WarningContainer,
    val codeBg: Color = OsColors.CodeBg,
    val codeBgHeader: Color = OsColors.CodeBgHeader
)

val LocalAgentOsColors = staticCompositionLocalOf { AgentOsColors() }

/** Material scheme mapped onto the OS palette (dark-first). */
private val DarkScheme = darkColorScheme(
    primary = OsColors.Accent,
    onPrimary = Color(0xFF0A0C12),
    primaryContainer = OsColors.SurfaceAccent,
    onPrimaryContainer = OsColors.TextPrimary,
    secondary = OsColors.AccentViolet,
    onSecondary = Color(0xFF0A0C12),
    secondaryContainer = OsColors.SurfaceAccent,
    onSecondaryContainer = OsColors.TextPrimary,
    tertiary = OsColors.Warning,
    background = OsColors.Bg,
    onBackground = OsColors.TextPrimary,
    surface = OsColors.Surface,
    onSurface = OsColors.TextPrimary,
    surfaceVariant = OsColors.SurfaceHigh,
    onSurfaceVariant = OsColors.TextSecondary,
    outline = OsColors.BorderStrong,
    outlineVariant = OsColors.Border,
    error = OsColors.Error,
    errorContainer = OsColors.ErrorContainer,
    onError = Color(0xFF2B0F12),
    onErrorContainer = Color(0xFFFFC9CD),
    surfaceContainer = OsColors.SurfaceHigh,
    surfaceContainerHigh = OsColors.SurfaceInteractive,
    inverseSurface = Color(0xFFE9EDF5)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D5BD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E7FF),
    onPrimaryContainer = Color(0xFF16224E),
    secondary = Color(0xFF6D4FC4),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF171B26),
    surface = Color.White,
    onSurface = Color(0xFF171B26),
    surfaceVariant = Color(0xFFECF0F8),
    onSurfaceVariant = Color(0xFF4A5468),
    outline = Color(0xFFC6CEDF),
    outlineVariant = Color(0xFFE1E6F0),
    error = Color(0xFFC0343F),
    errorContainer = Color(0xFFFFE1E3)
)

/* Shapes */
object OsShapes {
    val card: Shape = RoundedCornerShape(18.dp)
    val cardSmall: Shape = RoundedCornerShape(13.dp)
    val field: Shape = RoundedCornerShape(22.dp)
    val pill: Shape = RoundedCornerShape(50)
    val sheet: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val code: Shape = RoundedCornerShape(12.dp)
}

/**
 * AmbientBackdrop — a very soft accent bloom behind top-level screens.
 * Pure gradient draws (no RenderScript/blur) so it is safe on low-RAM devices.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val c = osColors()
    Canvas(modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        drawRect(c.bg)
        // primary bloom — top center
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.accent.copy(alpha = 0.085f), Color.Transparent),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, -h * 0.1f)
        )
        // secondary bloom — violet, upper right, dimmer
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.accentViolet.copy(alpha = 0.055f), Color.Transparent),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.12f)
        )
    }
}

@Composable
fun AgentOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAgentOsColors provides if (darkTheme) AgentOsColors() else lightOsColors()
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OsTypography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

/** Light-theme variant of the extended palette. */
private fun lightOsColors() = AgentOsColors(
    bg = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceHigh = Color(0xFFF2F4FA),
    surfaceInteractive = Color(0xFFE9EDF6),
    surfaceAccent = Color(0xFFE3EAFF),
    border = Color(0xFFE1E6F0),
    borderStrong = Color(0xFFC6CEDF),
    accent = Color(0xFF3D5BD9),
    accentViolet = Color(0xFF6D4FC4),
    accentDim = Color(0xFF9FB0E8),
    textPrimary = Color(0xFF171B26),
    textSecondary = Color(0xFF4A5468),
    textMuted = Color(0xFF8A93A6),
    success = Color(0xFF178A4C),
    successContainer = Color(0xFFDDF5E6),
    error = Color(0xFFC0343F),
    errorContainer = Color(0xFFFFE1E3),
    warning = Color(0xFF9A6A0F),
    warningContainer = Color(0xFFFFF0D4),
    codeBg = Color(0xFF10141C),
    codeBgHeader = Color(0xFF1A2030)
)

/** Convenience accessor. */
@Composable
fun osColors(): AgentOsColors = LocalAgentOsColors.current
