package dev.netvalve.ui.sim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.sim.SimMode
import dev.netvalve.sim.TrafficSimulator
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.components.StatTile
import dev.netvalve.utils.Format

/**
 * In-app traffic simulator (roadmap M5). The traffic runs under NetValve's own UID, which is always
 * Low, so it plays the heavy "Low app" next to PayProbe (High) in the evaluation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(viewModel: SimulatorViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var downloadUrl by rememberSaveable { mutableStateOf(TrafficSimulator.DEFAULT_DOWNLOAD_URL) }
    var uploadUrl by rememberSaveable { mutableStateOf(TrafficSimulator.DEFAULT_UPLOAD_URL) }
    var autoStop by rememberSaveable { mutableStateOf(TrafficSimulator.DEFAULT_AUTO_STOP_SEC.toString()) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Load") })
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Traffic simulator") {
                    Text(
                        "Creates heavy traffic as NetValve itself (always Low tier): " +
                            "${TrafficSimulator.DEFAULT_DOWNLOADS} parallel downloads and " +
                            "${TrafficSimulator.DEFAULT_UPLOADS} parallel uploads, each on its own connection. " +
                            "To send it through the tunnel, tick NetValve in the Apps list and restart the tunnel. " +
                            "It uses a lot of data (hotspot or mobile data), so it stops by itself after the auto-stop time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        label = { Text("Download URL (endless or large body)") },
                        singleLine = true,
                        enabled = !stats.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uploadUrl,
                        onValueChange = { uploadUrl = it },
                        label = { Text("Upload URL (accepts POST)") },
                        singleLine = true,
                        enabled = !stats.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoStop,
                        onValueChange = { autoStop = it },
                        label = { Text("Auto-stop after (seconds, 0 = never)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !stats.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (stats.running) {
                        Button(onClick = viewModel::stop) { Text("Stop") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.start(SimMode.DOWN, downloadUrl, uploadUrl, autoStop) }) { Text("Download") }
                            FilledTonalButton(onClick = { viewModel.start(SimMode.UP, downloadUrl, uploadUrl, autoStop) }) { Text("Upload") }
                            FilledTonalButton(onClick = { viewModel.start(SimMode.BOTH, downloadUrl, uploadUrl, autoStop) }) { Text("Both") }
                        }
                    }
                }
            }
            item {
                SectionCard(title = if (stats.running) "Running (${stats.mode}) · ${stats.elapsedSec} s" else "Last run") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("Download", Format.rate(stats.downBps))
                        StatTile("Upload", Format.rate(stats.upBps))
                        StatTile("Errors", stats.errors.toString())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("Total down", Format.bytes(stats.totalDown))
                        StatTile("Total up", Format.bytes(stats.totalUp))
                    }
                    stats.lastError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Last error: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
