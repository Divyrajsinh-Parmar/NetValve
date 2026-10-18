package dev.netvalve.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.BuildConfig
import dev.netvalve.data.model.Ipv6Mode
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.scheduler.RateUnits
import dev.netvalve.scheduler.SchedMode
import dev.netvalve.service.TunnelState
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.navigation.AppActions
import dev.netvalve.utils.Format

/**
 * Home screen. Top to bottom: is protection on (one big button), what is still missing (checklist),
 * Priority Control (the scheduler, in plain words), which apps are controlled and their priority,
 * optional phone setup, tunnel settings.
 *
 * "Priority Control" is the name people see. In the report the same feature is called the
 * priority pacer (Mode B).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    actions: AppActions,
    onOpenApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val steps = SetupChecklist.evaluate(
        tunnelRunning = state.running,
        onlySelectedMode = state.settings.selectionMode == SelectionMode.ONLY_SELECTED,
        selectedAppCount = state.selectedCount,
        priorityOn = state.settings.schedMode == SchedMode.PRIORITY_PACER,
        sharedDownBytesPerSec = state.settings.schedRateDownBytesPerSec,
        sharedUpBytesPerSec = state.settings.schedRateUpBytesPerSec,
        ownAppTicked = state.ownAppTicked,
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("NetValve") })
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusHero(state, actions, onPauseResume = viewModel::pauseResume) }

            if (!BuildConfig.USE_NETSTACK) {
                item {
                    SectionCard {
                        Text(
                            "Development engine (loopback)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This build does NOT forward traffic upstream, so controlled apps will lose " +
                                "connectivity while the tunnel is on (this is not a throttling bug). Build the " +
                                "netstack engine (./netstack/build-aar.sh, then -Pnetvalve.netstack=true) for real traffic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { ChecklistCard(steps, onOpenApps) }
            item { PriorityControlCard(state, viewModel) }
            item { ControlledAppsCard(state, onOpenApps, onOpenApp) }

            if (!state.usageAccessGranted) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Filled.Visibility, null) },
                        title = "Optional: usage access",
                        body = "Needed only for \"throttle in background only\" rules. Priority Control works without it.",
                        buttonText = "Grant usage access",
                        onClick = actions.requestUsageAccess,
                    )
                }
            }
            if (!state.ignoringBatteryOptimizations) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Filled.BatteryAlert, null) },
                        title = "Keep the tunnel alive",
                        body = "Exempt NetValve from battery optimization so Android does not stop the tunnel while the screen is off. Some phones need an extra auto-start step.",
                        buttonText = "Fix battery settings",
                        onClick = actions.requestBatteryExemption,
                        secondaryText = "Phone-specific settings",
                        onSecondary = actions.openVendorSettings,
                    )
                }
            }

            item { SettingsCard(state, viewModel) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1. Status: one big button, three big numbers
// ---------------------------------------------------------------------------------------------

@Composable
private fun StatusHero(state: DashboardUiState, actions: AppActions, onPauseResume: () -> Unit) {
    SectionCard(
        containerColor = if (state.running) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else null,
    ) {
        Text(
            when (state.status.state) {
                TunnelState.RUNNING -> "Protection is ON"
                TunnelState.PAUSED -> "Paused (traffic passes freely)"
                TunnelState.STARTING -> "Starting…"
                TunnelState.ERROR -> "Error"
                TunnelState.REVOKED -> "Stopped by another VPN"
                TunnelState.STOPPED -> "Protection is OFF"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        state.status.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(
            if (state.running) {
                "Running for ${Format.duration(state.stats.sessionDurationMillis)}"
            } else {
                "Press Start protection to send the chosen apps through NetValve."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BigStat("Download", Format.rate(state.stats.liveDownloadBps))
            BigStat("Upload", Format.rate(state.stats.liveUploadBps))
            BigStat("Connections", state.stats.activeConnections.toString())
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { actions.toggleVpn(!state.running) },
                modifier = Modifier.weight(1f),
                colors = if (state.running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (state.running) "Stop protection" else "Start protection")
            }
            if (state.running) {
                FilledTonalButton(onClick = onPauseResume) {
                    Icon(Icons.Filled.Bolt, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.status.state == TunnelState.PAUSED) "Resume" else "Pause")
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Checklist: what is still missing
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChecklistCard(steps: List<SetupStep>, onOpenApps: () -> Unit) {
    val done = SetupChecklist.doneCount(steps)
    SectionCard(
        title = if (SetupChecklist.allDone(steps)) "Ready to test" else "Setup checklist  ($done of ${steps.size} done)",
    ) {
        steps.forEachIndexed { i, step ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (step.state) {
                        StepState.DONE -> Icons.Filled.CheckCircle
                        StepState.WARN -> Icons.Filled.Warning
                        StepState.TODO -> Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = when (step.state) {
                        StepState.DONE -> MaterialTheme.colorScheme.primary
                        StepState.WARN -> MaterialTheme.colorScheme.tertiary
                        StepState.TODO -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        step.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step.id == SetupStepId.APPS && step.state == StepState.TODO) {
                        TextButton(onClick = onOpenApps) { Text("Open Apps") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Priority Control (the scheduler)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PriorityControlCard(state: DashboardUiState, viewModel: DashboardViewModel) {
    val s = state.settings
    val on = s.schedMode == SchedMode.PRIORITY_PACER
    val noSpeed = s.schedRateDownBytesPerSec <= 0L && s.schedRateUpBytesPerSec <= 0L

    var measuredDown by remember { mutableStateOf("") }
    var measuredUp by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var downText by remember(s.schedRateDownBytesPerSec) { mutableStateOf(RateUnits.formatMbps(s.schedRateDownBytesPerSec)) }
    var upText by remember(s.schedRateUpBytesPerSec) { mutableStateOf(RateUnits.formatMbps(s.schedRateUpBytesPerSec)) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    SectionCard(title = "Priority Control") {
        Text(
            "When apps compete for the internet, important apps go first: High, then Medium, then Low. " +
                "Low apps get what is left.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = if (on) "Priority Control is ON" else "Priority Control is OFF",
            subtitle = null,
            checked = on,
            onCheckedChange = { viewModel.setSchedEnabled(it) },
        )
        if (on && noSpeed) {
            Text(
                "It is ON but no shared speed is set yet, so nothing is prioritised. Set the speed below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Shared speed", style = MaterialTheme.typography.titleMedium)
        Text(
            "The total speed the controlled apps share. Use about 90% of your real internet speed " +
                "(measure it with the Load tab while Priority Control is OFF).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Now: ${RateUnits.formatMbps(s.schedRateDownBytesPerSec)} Mbps download · " +
                "${RateUnits.formatMbps(s.schedRateUpBytesPerSec)} Mbps upload (0 = not limited)",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(12.dp))
        Text("Your measured speed", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = measuredDown,
                onValueChange = { measuredDown = it; message = null },
                label = { Text("Download (Mbps)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = measuredUp,
                onValueChange = { measuredUp = it; message = null },
                label = { Text("Upload (Mbps)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val d = RateUnits.shareTextOf(measuredDown)
            val u = RateUnits.shareTextOf(measuredUp)
            val dm = d?.let { RateUnits.parseMbps(it) }
            val um = u?.let { RateUnits.parseMbps(it) }
            if (d == null || u == null || dm == null || um == null) {
                message = "Enter both measured speeds as numbers above 0, for example 25 and 8."
                messageIsError = true
            } else {
                viewModel.setSchedRates(downMbps = dm, upMbps = um)
                message = "Saved: shared speed is now $d Mbps download and $u Mbps upload."
                messageIsError = false
            }
        }) { Text("Use 90% as shared speed") }

        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Hide exact values" else "Type exact values instead")
        }
        if (advanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = downText,
                    onValueChange = { downText = it; message = null },
                    label = { Text("Shared download (Mbps)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = upText,
                    onValueChange = { upText = it; message = null },
                    label = { Text("Shared upload (Mbps)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = {
                val down = RateUnits.parseMbps(downText)
                val up = RateUnits.parseMbps(upText)
                if (down == null || up == null) {
                    message = "Enter numbers from 0 to ${RateUnits.MAX_MBPS.toInt()} (for example 22.5 or 8)."
                    messageIsError = true
                } else {
                    viewModel.setSchedRates(downMbps = down, upMbps = up)
                    message = "Saved."
                    messageIsError = false
                }
            }) { Text("Save exact values") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Per-app speed limits you set elsewhere still apply on top.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Controlled apps with their priority
// ---------------------------------------------------------------------------------------------

@Composable
private fun ControlledAppsCard(state: DashboardUiState, onOpenApps: () -> Unit, onOpenApp: (String) -> Unit) {
    SectionCard(title = "Controlled apps") {
        val modeText = when (state.settings.selectionMode) {
            SelectionMode.ONLY_SELECTED -> "Only the ${state.selectedCount} ticked app(s) go through NetValve."
            SelectionMode.ALL_EXCEPT -> "All apps go through NetValve except the ${state.selectedCount} ticked ones."
        }
        Text(modeText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        if (state.controlledApps.isEmpty()) {
            Text(
                "No apps ticked yet. Open the Apps tab and tick the apps to control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val ranked = state.controlledApps.count { it.rank > 0 }
            val normal = state.controlledApps.size - ranked
            Text(
                "In the priority list $ranked · normal $normal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.controlledApps.take(12).forEach { app ->
                    AssistChip(
                        onClick = { onOpenApp(app.packageName) },
                        label = { Text("${app.label} · ${rankName(app.rank)}") },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onOpenApps) { Text("Choose apps and priority") }
    }
}

private fun rankName(rank: Int): String = if (rank > 0) "#$rank" else "Normal"

// ---------------------------------------------------------------------------------------------
// 5. Phone setup and tunnel settings
// ---------------------------------------------------------------------------------------------

@Composable
private fun SetupCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClick) { Text(buttonText) }
            if (secondaryText != null && onSecondary != null) {
                FilledTonalButton(onClick = onSecondary) { Text(secondaryText) }
            }
        }
    }
}

@Composable
private fun SettingsCard(state: DashboardUiState, viewModel: DashboardViewModel) {
    SectionCard(title = "Tunnel settings") {
        SwitchRow(
            label = "Control only the apps I pick",
            subtitle = "Recommended. Off = every app goes through the tunnel except the ones you tick.",
            checked = state.settings.selectionMode == SelectionMode.ONLY_SELECTED,
            onCheckedChange = {
                viewModel.setSelectionMode(if (it) SelectionMode.ONLY_SELECTED else SelectionMode.ALL_EXCEPT)
                viewModel.applyAndMaybeRestart()
            },
        )
        SwitchRow(
            label = "Pass IPv6 traffic through",
            subtitle = "Leave ON. Off = IPv6 is refused quickly so apps fall back to IPv4.",
            checked = state.settings.ipv6Mode == Ipv6Mode.RELAY,
            onCheckedChange = {
                viewModel.setIpv6Mode(if (it) Ipv6Mode.RELAY else Ipv6Mode.FAST_REJECT)
                viewModel.applyAndMaybeRestart()
            },
        )
        SwitchRow(
            label = "Keep name lookups (DNS) fast",
            subtitle = "Leave ON. DNS is never slowed down, so pages and apps start quickly.",
            checked = state.settings.exemptDns,
            onCheckedChange = { viewModel.setExemptDns(it) },
        )
        SwitchRow(
            label = "Start automatically after the phone restarts",
            subtitle = "Leave OFF while testing.",
            checked = state.settings.autoStartOnBoot,
            onCheckedChange = { viewModel.setAutoStart(it) },
        )
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
