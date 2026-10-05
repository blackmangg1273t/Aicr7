package com.agentos.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.components.rememberHaptics
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/**
 * 2026 premium composer — a single clean field with a subtle accent glow
 * while the user is typing or focused. The glow appears whenever input is
 * non-empty OR focused, so the field gently "comes alive" without screaming.
 * No big buttons, no decorative chrome — let the field breathe.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask AgentOS anything…"
) {
    val c = osColors()
    val haptic = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val hasContent = value.isNotEmpty()
    // Subtle glow while focused; even subtler when just has content (no focus).
    val glowTarget = when {
        focused -> 0.55f
        hasContent -> 0.18f
        else -> 0f
    }
    val borderColor by animateColorAsState(
        when {
            focused -> c.accent.copy(alpha = 0.40f)
            hasContent -> c.borderStrong
            else -> c.border
        },
        animationSpec = tween(OsMotion.Normal),
        label = "composerBorder"
    )
    val glow by animateFloatAsState(
        glowTarget,
        animationSpec = tween(OsMotion.Normal),
        label = "composerGlow"
    )
    val sendEnabled = value.isNotBlank() && enabled
    val sendScale by animateFloatAsState(if (sendEnabled) 1f else 0.86f, animationSpec = tween(OsMotion.Fast), label = "sendScale")

    fun submit() {
        if (sendEnabled) {
            haptic()
            onSend()
        }
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(OsShapes.field)
                .drawBehind {
                    if (glow > 0.01f) {
                        // Outer soft halo — a radial bloom around the field.
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    c.accent.copy(alpha = glow * 0.22f),
                                    c.accentViolet.copy(alpha = glow * 0.14f),
                                    androidx.compose.ui.graphics.Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(
                                    size.width * 0.18f,
                                    size.height * 0.5f
                                ),
                                radius = size.maxDimension * 0.85f
                            ),
                            cornerRadius = CornerRadius(22.dp.toPx())
                        )
                        // Inner stroke — accent border line.
                        val stroke = 4.dp.toPx()
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    c.accent.copy(alpha = glow * 0.55f),
                                    c.accentViolet.copy(alpha = glow * 0.35f)
                                )
                            ),
                            cornerRadius = CornerRadius(22.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                        )
                    }
                }
                .background(c.surfaceInteractive)
                .border(1.dp, borderColor, OsShapes.field)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Leading "✦" mark — a quiet hint that this is an AI input.
                Text(
                    "✦",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (hasContent || focused) c.accent.copy(alpha = 0.85f) else c.textMuted
                )
                Box(Modifier.weight(1f).heightIn(min = 22.dp)) {
                    if (value.isEmpty() && !focused) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textMuted
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        ),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SendButton(enabled = sendEnabled, scale = sendScale, onClick = { submit() })
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, scale: Float, onClick: () -> Unit) {
    val c = osColors()
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (enabled) c.accent else c.surfaceInteractive,
        animationSpec = tween(OsMotion.Fast), label = "sendBg"
    )
    val sendSize by animateDpAsState(if (enabled) 34.dp else 30.dp, animationSpec = tween(OsMotion.Fast), label = "sendSize")
    Box(
        Modifier
            .scale(scale)
            .size(sendSize)
            .clip(CircleShape)
            .then(if (enabled) Modifier.drawBehind {
                // very subtle outer ring glow on the send button when active.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c.accent.copy(alpha = 0.35f),
                            androidx.compose.ui.graphics.Color.Transparent
                        ),
                        radius = sendSize.toPx() * 0.95f
                    )
                )
            } else Modifier)
            .background(bg, CircleShape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null
                ) { onClick() } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = "Send",
            tint = if (enabled) androidx.compose.ui.graphics.Color(0xFF0A0C12) else c.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
