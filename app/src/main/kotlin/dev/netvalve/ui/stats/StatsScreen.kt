package dev.netvalve.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.health.HealthLevel
import dev.netvalve.scheduler.RateUnits
import dev.netvalve.ui.components.LabeledRow
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.components.StatTile
import dev.netvalve.utils.Format
import java.util.Locale

/**
 * The simple Stats tab (patch B3): one easy status, a few big numbers, no technical words.
 * The old technical statistics are in Test tools ([TechnicalStatsScreen]).
 */
@Composable
fun StatsScreen(
    onShare: (String) -> Unit,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = state.counters
    val verdict = state.verdict

    val statusColor = when (verdict.level) {
        HealthLevel.GOOD -> Color(0xFF2E7D32)
        HealthLevel.CHECK, HealthLevel.NO_SPEED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val all = c.allBytes()
    val priorityShare = if (all > 0L) String.format(Locale.US, "%.0f %%", 100.0 * c.priorityBytes() / all) else "no data"
    val priorityWait = c.priorityAvgWaitMs()?.let { String.format(Locale.US, "%.0f ms", it) } ?: "no data"
    val normalWait = c.normalAvgWaitMs()?.let { String.format(Locale.US, "%.0f ms", it) } ?: "no data"

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Stats") },
            actions = { TextButton(onClick = { onShare(viewModel.reportText()) }) { Text("Share") } },
        )
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text(verdict.title, style = MaterialTheme.typography.titleLarge, color = statusColor)
                    Spacer(Modifier.height(4.dp))
                    Text(verdict.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionCard(title = "Time") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("Total running time", Format.duration(c.runningMs))
                        StatTile("Longest run", Format.duration(c.longestRunMs))
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("Times protection started", c.tunnelStarts.toString())
                }
            }
            item {
                SectionCard(title = "Priority apps") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("Their share of data", priorityShare)
                        StatTile("Their wait", priorityWait)
                        StatTile("Other apps wait", normalWait)
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("Data of priority apps", Format.bytes(c.priorityBytes()))
                    LabeledRow("Data of other apps", Format.bytes(c.normalBytes()))
                    Text(
                        "Wait = how long data had to wait for its turn (average). It is measured only while a shared speed is set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = "Problems") {
                    LabeledRow("Protection could not start", c.establishFailures.toString())
                    LabeledRow("Other errors", c.engineErrors.toString())
                    LabeledRow("Speed measuring failed", c.autoSpeedFailures.toString())
                    LabeledRow("Other apps' connections that did not open", c.connectFailures.toString())
                    Text(
                        "The last line is usually an app asking for an address that cannot be reached; it is not a fault of NetValve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = "Measured internet speed") {
                    state.speeds.forEach { (name, bytesPerSec) ->
                        LabeledRow(name, if (bytesPerSec == null || bytesPerSec <= 0L) "not measured yet" else "${RateUnits.formatMbps(bytesPerSec)} Mbps")
                    }
                    Text(
                        "This is the shared speed NetValve uses (a bit below your real speed). It is measured again by itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
