package dev.netvalve.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Test tools: the technical screens of the evaluation stage, kept but hidden from normal use.
 * Opened by tapping the small "NetValve" text on the Home screen seven times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpenAdvancedHome: () -> Unit,
    onOpenAdvancedApps: () -> Unit,
    onOpenLoad: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenTechnicalStats: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Test tools") })
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "These screens are only for testing. Normal use needs none of them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenAdvancedHome, modifier = Modifier.fillMaxWidth()) {
                Text("Advanced home (shared speed, tunnel settings)")
            }
            FilledTonalButton(onClick = onOpenAdvancedApps, modifier = Modifier.fillMaxWidth()) {
                Text("Technical app list and per-app rules")
            }
            FilledTonalButton(onClick = onOpenLoad, modifier = Modifier.fillMaxWidth()) {
                Text("Load simulator")
            }
            FilledTonalButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                Text("Logs")
            }
            FilledTonalButton(onClick = onOpenTechnicalStats, modifier = Modifier.fillMaxWidth()) {
                Text("Technical statistics (per app)")
            }
        }
    }
}
