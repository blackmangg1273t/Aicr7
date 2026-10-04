package com.agentos.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.components.rememberHaptics
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/**
 * Premium composer: layered field with TRUE focus glow (interaction state,
 * not text presence), IME send support, adaptive send button and smooth
 * multi-line expansion.
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

    val borderColor by animateColorAsState(
        when {
            focused -> c.accent.copy(alpha = 0.45f)
            value.isNotEmpty() -> c.borderStrong
            else -> c.border
        },
        animationSpec = androidx.compose.animation.core.tween(OsMotion.Fast), label = "border"
    )
    val glow by animateFloatAsState(if (focused) 0.55f else 0f, animationSpec = androidx.compose.animation.core.tween(OsMotion.Fast), label = "glow")
    val sendEnabled = value.isNotBlank() && enabled
    val sendScale by animateFloatAsState(if (sendEnabled) 1f else 0.86f, animationSpec = androidx.compose.animation.core.tween(OsMotion.Fast), label = "sendScale")

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
                        val stroke = 5.dp.toPx()
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    c.accent.copy(alpha = glow * 0.6f),
                                    c.accentViolet.copy(alpha = glow * 0.4f)
                                )
                            ),
                            cornerRadius = CornerRadius(22.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                        )
                    }
                }
                .background(c.surfaceInteractive)
                .border(1.dp, borderColor, OsShapes.field)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 24.dp)) {
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
        }

        // action row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    !enabled -> "AgentOS is working…"
                    value.length > 120 -> "${value.length} chars"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f)
            )
            SendButton(enabled = sendEnabled, scale = sendScale, onClick = { submit() })
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, scale: Float, onClick: () -> Unit) {
    val c = osColors()
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (enabled) c.accent else c.surfaceInteractive,
        animationSpec = androidx.compose.animation.core.tween(OsMotion.Fast), label = "sendBg"
    )
    val width by animateDpAsState(if (enabled) 46.dp else 40.dp, animationSpec = androidx.compose.animation.core.tween(OsMotion.Fast), label = "sendW")
    Box(
        Modifier
            .scale(scale)
            .size(width = width, height = 40.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(listOf(c.accent.copy(alpha = 0.5f), c.accentViolet.copy(alpha = 0.5f))),
                    cornerRadius = CornerRadius(40f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
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
            modifier = Modifier.size(20.dp)
        )
    }
}
