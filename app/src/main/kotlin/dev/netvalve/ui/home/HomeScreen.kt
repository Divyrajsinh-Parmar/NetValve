package dev.netvalve.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.service.TunnelState
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.dashboard.DashboardViewModel
import dev.netvalve.ui.navigation.AppActions

/**
 * Production Home screen: protection on/off, one big Pause button, nothing else. The old technical
 * Home (checklist, shared speed, tunnel settings) lives in Test tools.
 *
 * Tapping the small "NetValve" text at the bottom seven times opens Test tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    actions: AppActions,
    onOpenTools: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var taps by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.applyProductionDefaults() }

    val tunnelState = state.status.state
    val paused = tunnelState == TunnelState.PAUSED

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("NetValve") })
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    containerColor = if (state.running && !paused) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        null
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (tunnelState) {
                                    TunnelState.RUNNING -> "Protection is ON"
                                    TunnelState.PAUSED -> "Paused"
                                    TunnelState.STARTING -> "Starting…"
                                    TunnelState.ERROR -> "Something went wrong"
                                    TunnelState.REVOKED -> "Stopped by another VPN app"
                                    TunnelState.STOPPED -> "Protection is OFF"
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (tunnelState) {
                                    TunnelState.RUNNING -> "Your priority apps get the internet first."
                                    TunnelState.PAUSED -> "All apps use the internet freely until you resume."
                                    TunnelState.STOPPED -> "Switch on to protect your priority apps."
                                    else -> state.status.message ?: "Switch on to try again."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.running,
                            onCheckedChange = { actions.toggleVpn(it) },
                        )
                    }
                }
            }

            if (state.running) {
                item {
                    Button(
                        onClick = viewModel::pauseResume,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text(
                            if (paused) "Resume protection" else "Pause protection",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Priority apps") {
                    Text(
                        "Choose your priority apps and their order on the Apps tab. " +
                            "If the internet ever feels wrong, press Pause. " +
                            "Banking and payment apps may not work while protection is on: switch it off for them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    "NetValve",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            taps += 1
                            if (taps >= 7) {
                                taps = 0
                                onOpenTools()
                            }
                        }
                        .padding(vertical = 16.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
