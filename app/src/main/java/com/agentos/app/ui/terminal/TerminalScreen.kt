package com.agentos.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/** Direct real shell access (Termux transport or app sandbox). */
@Composable
fun TerminalScreen(uiState: TerminalUiState, onRun: (String) -> Unit, onTestTermux: () -> Unit) {
    val c = osColors()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) listState.animateScrollToItem(uiState.lines.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c.surfaceInteractive),
                contentAlignment = Alignment.Center
            ) { Text("›_", style = MonoStyle, color = c.success) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    if (uiState.termuxAvailable) "Termux transport · full Linux userland"
                    else "App sandbox shell · install Termux for full userland",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
            AssistChip(
                onClick = onTestTermux,
                label = { Text("Test", style = MaterialTheme.typography.labelMedium) },
                enabled = !uiState.busy
            )
        }

        // output
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(uiState.lines) { line ->
                    Text(
                        line.text,
                        style = MonoStyle,
                        color = when {
                            line.command -> c.accent
                            else -> c.textSecondary
                        }
                    )
                }
                if (uiState.lines.isEmpty()) {
                    item {
                        Text(
                            "Run real commands — output, errors and exit codes are captured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted
                        )
                    }
                }
            }
        }

        // composer
        Box(Modifier.background(c.bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(OsShapes.field)
                    .background(c.surfaceInteractive)
                    .border(1.dp, c.border, OsShapes.field)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text("command (e.g. ls -la)", style = MonoStyle, color = c.textMuted)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (input.isNotBlank()) { onRun(input); input = "" }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.busy,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            cursorColor = c.accent,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary
                        ),
                        textStyle = MonoStyle
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { onRun(input); input = "" },
                    enabled = input.isNotBlank() && !uiState.busy,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (input.isNotBlank()) c.accent else c.surfaceHigh,
                        contentColor = if (input.isNotBlank()) androidx.compose.ui.graphics.Color(0xFF0A0C12) else c.textMuted
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Run", Modifier.size(18.dp))
                }
            }
        }
    }
}
