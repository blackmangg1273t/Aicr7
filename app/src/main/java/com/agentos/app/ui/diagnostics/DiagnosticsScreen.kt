package com.agentos.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/* Diagnostics — a quick "is everything still alive?" dashboard.       */
/* ------------------------------------------------------------------ */

/**
 * @param onClose invoked when the user taps the Close affordance at the top
 *   of the screen (typically returns to Settings).
 */
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onClose: () -> Unit) {
    val c = osColors()
    val state by viewModel.state.collectAsState()

    Surface(Modifier.fillMaxSize(), color = c.bg) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Top bar: title + Close + Refresh
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Close",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier
                        .clip(OsShapes.pill)
                        .background(c.accent.copy(alpha = 0.12f))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Last updated: ${formatTimestamp(state.lastUpdated)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
            Spacer(Modifier.height(16.dp))

            // Services group
            SectionLabel("Services")
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.Accessibility,
                name = "Accessibility Service",
                ok = state.accessibilityEnabled,
                okLabel = "Connected",
                badLabel = "Disabled",
                description = if (state.accessibilityEnabled)
                    "AgentOS can read the screen and dispatch gestures."
                else
                    "Enable 'AgentOS Android Agent' in Settings > Accessibility."
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.Devices,
                name = "Overlay",
                ok = state.overlayEnabled,
                okLabel = "Enabled",
                badLabel = "Disabled",
                description = if (state.overlayEnabled)
                    "A floating progress card can appear over other apps."
                else
                    "Grant 'display over other apps' permission to see the floating card."
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.AutoAwesome,
                name = "Foreground Service",
                ok = state.foregroundServiceRunning,
                okLabel = "Running",
                badLabel = "Stopped",
                description = "Best-effort flag toggled by TaskForegroundService lifecycle."
            )

            Spacer(Modifier.height(20.dp))

            // Engines group
            SectionLabel("Engines")
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.Web,
                name = "Browser Automation",
                ok = true,
                okLabel = "Ready",
                badLabel = "Unavailable",
                description = "Headless WebView is lazy — ready on demand."
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.Devices,
                name = "Task Engine",
                ok = state.taskEngineRunning,
                okLabel = "Running",
                badLabel = "Idle",
                description = if (state.taskEngineRunning)
                    "At least one task is active right now."
                else
                    "No tasks currently running."
            )

            Spacer(Modifier.height(20.dp))

            // Provider group
            SectionLabel("AI")
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Default.Cloud,
                name = "AI Provider",
                ok = state.aiProviderConfigured,
                okLabel = "Connected",
                badLabel = "Not configured",
                description = if (state.aiProviderConfigured)
                    "A primary provider is selected and has an API key."
                else
                    "Add an API key in Settings → AI to enable AI features."
            )

            Spacer(Modifier.height(20.dp))

            // Last activity group
            SectionLabel("Last activity")
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = Icons.Default.BugReport,
                name = "Last task",
                value = state.lastTaskStatus?.let { status ->
                    "$status · ${formatTimestamp(state.lastTaskTime)}"
                } ?: "—",
                valueColor = c.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = Icons.Default.Warning,
                name = "Last error",
                value = state.lastError ?: "—",
                valueColor = if (state.lastError != null) c.error else c.textMuted
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = Icons.Default.Refresh,
                name = "Last recovery",
                value = state.lastRecoveryNote ?: "—",
                valueColor = if (state.lastRecoveryNote != null) c.warning else c.textMuted
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = osColors()
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = c.accent,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * A row with a small icon, a name, a status pill, and an optional description.
 * The pill is green for "ok" states and dim/red for "not ok" states.
 */
@Composable
private fun StatusRow(
    icon: ImageVector,
    name: String,
    ok: Boolean,
    okLabel: String,
    badLabel: String,
    description: String
) {
    val c = osColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, OsShapes.card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background((if (ok) c.success else c.warning).copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (ok) c.success else c.warning,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(ok = ok, okLabel = okLabel, badLabel = badLabel)
        }
    }
}

/** Row with a small icon, a name, and a value (no status pill). */
@Composable
private fun InfoRow(icon: ImageVector, name: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val c = osColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, OsShapes.card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.surfaceInteractive),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusPill(ok: Boolean, okLabel: String, badLabel: String) {
    val c = osColors()
    val color = if (ok) c.success else c.warning
    Row(
        Modifier
            .clip(OsShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.30f), OsShapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            if (ok) okLabel else badLabel,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTimestamp(ts: Long?): String {
    if (ts == null || ts == 0L) return "—"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return fmt.format(Date(ts))
}
