package dev.netvalve.ui.demo

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.demo.DemoPhase
import dev.netvalve.demo.DemoProgress
import dev.netvalve.demo.DemoStep
import dev.netvalve.demo.LagScore
import dev.netvalve.demo.PhaseResult
import dev.netvalve.ui.components.LabeledRow
import dev.netvalve.ui.components.SectionCard
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The live demo screen (patch E1): "before" (protection paused) and "after" (protection on) with a big
 * download running, measured by small probes. The numbers are probe-based proxies (see DemoReport).
 */
@Composable
fun DemoScreen(
    onShare: (String) -> Unit,
    viewModel: DemoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = state.progress
    val results = progress?.results.orEmpty()
    val before = results.firstOrNull { it.phase == DemoPhase.BEFORE }
    val after = results.firstOrNull { it.phase == DemoPhase.AFTER }
    val idle = results.firstOrNull { it.phase == DemoPhase.IDLE }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Live demo") },
            actions = {
                if (before != null && after != null && !state.running) {
                    TextButton(onClick = { onShare(viewModel.reportText()) }) { Text("Share") }
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text("See the difference yourself", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A big download runs in the background while small test requests measure the lag, first with priority " +
                            "paused (before; the tunnel stays on), then with priority on (after). It takes about one minute and uses roughly 100 to 300 MB of data. " +
                            "Before it starts, the app checks that the test servers answer. Your internet blinks for a few seconds at the start and at the end (the protection restarts).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.running) {
                        OutlinedButton(onClick = viewModel::cancel) { Text("Cancel") }
                    } else {
                        Button(onClick = viewModel::start) { Text(if (results.isEmpty()) "Start demo" else "Run again") }
                    }
                    state.notStarted?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (progress?.step == DemoStep.FAILED) {
                        Spacer(Modifier.height(8.dp))
                        Text(progress?.message ?: "The demo failed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.running && progress != null) {
                item { RunningCard(progress) }
            }
            if (before != null && after != null && !state.running && !(progress?.valid ?: true)) {
                item {
                    SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text("This run is NOT valid", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The big download did not run (below 1 Mbps), so there was no load and Before and After cannot be compared.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        listOfNotNull(before.loadProblem, after.loadProblem).distinct().forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Please press Share and send the text.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (before != null && after != null && !state.running && (progress?.valid ?: true)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResultCard("Before", before, Modifier.weight(1f))
                        ResultCard("After", after, Modifier.weight(1f))
                    }
                }
                item {
                    SectionCard(title = "How it feels") {
                        Text(
                            "Each dot moves one step per test request and waits as long as that request took. A slow line makes the dot lag behind.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Before", style = MaterialTheme.typography.labelMedium)
                        LagTrack(before.samples, MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text("After", style = MaterialTheme.typography.labelMedium)
                        LagTrack(after.samples, Color(0xFF2E7D32))
                    }
                }
                item {
                    SectionCard(title = "Notes") {
                        idle?.let {
                            LabeledRow("Quiet line (no download)", "${it.stats.medianMs ?: "no data"} ms median")
                        }
                        Text(
                            "These are probe-based numbers (a small web request every half second), not the game's own ping or real packet loss. " +
                                "The score limits are our own choice. One run on one network is one sample; on some networks there is no queue and the " +
                                "difference is small.",
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

@Composable
private fun RunningCard(p: DemoProgress) {
    val title = when (p.step) {
        DemoStep.PREPARING -> "Getting ready"
        DemoStep.IDLE -> "Step 1 of 3: quiet line"
        DemoStep.BEFORE -> "Step 2 of 3: BEFORE (priority paused, big download)"
        DemoStep.AFTER -> "Step 3 of 3: AFTER (priority on, big download)"
        DemoStep.RESTORING -> "Putting everything back"
        else -> ""
    }
    SectionCard(title = title) {
        p.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (p.step != DemoStep.PREPARING) Text(
            "${p.msLeft / 1000 + 1} s left" + (p.liveMs?.let { "   ·   last test: $it ms" } ?: if (p.step == DemoStep.PREPARING) "" else "   ·   last test: no answer"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        LatencyGraph(p.phaseSamples)
    }
}

@Composable
private fun ResultCard(title: String, r: PhaseResult, modifier: Modifier) {
    val (label, color) = when (r.score) {
        LagScore.GOOD -> "Good" to Color(0xFF2E7D32)
        LagScore.OKAY -> "Okay" to Color(0xFFB26A00)
        LagScore.BAD -> "Laggy" to MaterialTheme.colorScheme.error
        LagScore.NO_DATA -> "No data" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SectionCard(title = title, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = color)
        Spacer(Modifier.height(4.dp))
        Text("Ping ${r.stats.medianMs?.let { "$it ms" } ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        Text("Worst 5%: ${r.stats.p95Ms?.let { "$it ms" } ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        Text("Jitter ${r.stats.jitterMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        Text(String.format(Locale.US, "Lost %.0f %%", r.stats.lossPercent), style = MaterialTheme.typography.bodyMedium)
        Text(String.format(Locale.US, "Download %.0f Mbps", r.loadBytesPerSec * 8 / 1_000_000.0), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A line of the probe times; a lost probe is drawn at the top. */
@Composable
private fun LatencyGraph(samples: List<Long?>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val lostColor = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        if (samples.size < 2) return@Canvas
        val top = maxOf(300L, samples.filterNotNull().maxOrNull() ?: 300L).toFloat()
        val stepX = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, v ->
            val y = size.height - (((v ?: top.toLong()).toFloat() / top).coerceIn(0f, 1f) * size.height)
            val x = i * stepX
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (v == null) drawCircle(lostColor, radius = 4f, center = Offset(x, 0f))
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
}

/** A dot that takes one step per probe and waits as long as the probe took (a lost probe waits 1.5 s). */
@Composable
private fun LagTrack(samples: List<Long?>, color: Color) {
    var progress by remember(samples) { mutableFloatStateOf(0f) }
    val track = MaterialTheme.colorScheme.outlineVariant
    LaunchedEffect(samples) {
        if (samples.isEmpty()) return@LaunchedEffect
        while (true) {
            progress = 0f
            samples.forEachIndexed { i, v ->
                delay((v ?: 1_500L).coerceIn(10L, 1_500L))
                progress = (i + 1f) / samples.size
            }
            delay(1_200)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
        drawCircle(color, radius = 10f, center = Offset(10f + progress * (size.width - 20f), y))
    }
}
