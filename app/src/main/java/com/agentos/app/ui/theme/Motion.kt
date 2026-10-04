package com.agentos.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Unified motion system:
 *  - fast    140ms  (press, chip toggles, small fades)
 *  - normal  240ms  (appearance, navigation, sheets)
 *  - emphasis 420ms (task start, plan reveal, completion)
 */
object OsMotion {
    const val Fast = 140
    const val Normal = 240
    const val Emphasis = 420

    val Ease: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** Aura breathing period (ms) — slow, calm. */
    const val AuraPeriodMs = 2600
}

/** Smooth breathing value used by running-state glows; usable with `by`. */
@Composable
fun rememberBreathing(min: Float = 0.35f, max: Float = 0.8f): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "breathing")
    val raw = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = OsMotion.AuraPeriodMs
                0f at 0 with LinearEasing
                1f at OsMotion.AuraPeriodMs / 2 with LinearEasing
                0f at OsMotion.AuraPeriodMs with LinearEasing
            }
        ),
        label = "breath"
    )
    return androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { min + (max - min) * raw.value }
    }
}
