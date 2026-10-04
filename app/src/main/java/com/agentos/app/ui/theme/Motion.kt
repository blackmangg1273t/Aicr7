package com.agentos.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue

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

    /** Stagger step between list entrances (ms). */
    const val StaggerStepMs = 55L
}

/** Spring presets — physical, purposeful movement. */
object OsSprings {
    /** Quick settle for chips / toggles. */
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Friendly overshoot for completion / success reveals. */
    fun <T> celebrate() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Default smooth motion. */
    fun <T> gentle() = spring<T>(dampingRatio = 0.85f, stiffness = 380f)
}

/** Smooth breathing value used by running-state glows; usable with `by`. */
@Composable
fun rememberBreathing(min: Float = 0.35f, max: Float = 0.8f): State<Float> {
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
    return remember {
        derivedStateOf { min + (max - min) * raw.value }
    }
}

/**
 * Staggered entrance value for lists/empty-states: each item fades and
 * slides up slightly, offset by its index. Runs once per composition.
 */
@Composable
fun rememberStaggeredEntrance(index: Int, delayStepMs: Long = OsMotion.StaggerStepMs): State<Float> {
    var started by remember { mutableStateOf(false) }
    val progress = animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(OsMotion.Normal, easing = OsMotion.EaseOut),
        label = "stagger$index"
    )
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(8)) * delayStepMs)
        started = true
    }
    return progress
}

/**
 * One-second ticker that runs only while [active]. Returns elapsed seconds.
 * Used for real "time working" chips — grounded in actual wall time.
 */
@Composable
fun rememberElapsedSeconds(active: Boolean, resetKey: Any? = null): Int {
    var seconds by remember(resetKey) { mutableIntStateOf(0) }
    LaunchedEffect(active, resetKey) {
        if (!active) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1_000)
            seconds++
        }
    }
    return seconds
}
