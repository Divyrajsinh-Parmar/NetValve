package dev.netvalve.demo

import dev.netvalve.di.AppScope
import dev.netvalve.log.LogCategory
import dev.netvalve.log.Logger
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.service.TunnelState
import dev.netvalve.service.VpnController
import dev.netvalve.service.VpnStatus
import dev.netvalve.sim.SimMode
import dev.netvalve.sim.TrafficSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the demo screen shows. */
data class DemoUiState(
    val running: Boolean = false,
    val progress: DemoProgress? = null,
    /** A reason why the demo did not start (nothing was changed then). */
    val notStarted: String? = null,
)

/**
 * Runs the live demo (patch E1) with the real tunnel, the traffic simulator and a probe to a fixed
 * server. It lives in the app scope, so it goes on when the screen is left; [cancel] stops it.
 * Everything it changes is put back by [DemoOrchestrator] (see [DemoControls.leave]).
 */
@Singleton
class DemoRunner @Inject constructor(
    private val controller: VpnController,
    private val simulator: TrafficSimulator,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
    @AppScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    @Volatile private var job: Job? = null

    fun start() {
        if (_state.value.running) return
        _state.value = DemoUiState(running = true, progress = DemoProgress(DemoStep.PREPARING))
        job = scope.launch {
            try {
                val problem = checkReady()
                if (problem != null) {
                    _state.value = DemoUiState(running = false, notStarted = problem)
                    return@launch
                }
                // Before anything is changed: which test servers answer from this network right now (IPv6 and IPv4 tried in turn)?
                _state.value = DemoUiState(
                    running = true,
                    progress = DemoProgress(DemoStep.PREPARING, message = "Checking that the test servers answer (up to 20 seconds)"),
                )
                val chooser = ServerChooser()
                val probeIp = withContext(Dispatchers.IO) { chooser.firstReachable(PROBE_HOST, 80) }
                if (probeIp == null) {
                    logger.w(LogCategory.SYSTEM, "demo: not started, $PROBE_HOST does not answer")
                    _state.value = DemoUiState(running = false, notStarted = "The small test server ($PROBE_HOST) does not answer from this network right now. Nothing was changed.")
                    return@launch
                }
                val download = withContext(Dispatchers.IO) { chooser.chooseDownload(DownloadSources.ALL) }
                if (download == null) {
                    val names = DownloadSources.ALL.joinToString(", ") { it.name }
                    logger.w(LogCategory.SYSTEM, "demo: not started, no download server answers ($names)")
                    _state.value = DemoUiState(
                        running = false,
                        notStarted = "No download server answers from this network right now (tried: $names). Nothing was changed. Try again later or on another network.",
                    )
                    return@launch
                }
                logger.i(LogCategory.SYSTEM, "demo: started (probe $PROBE_HOST $probeIp; download ${download.source.name} ${download.address})")
                val outcome = DemoOrchestrator(controlsFor(probeIp), loadGeneratorFor(download), HttpProbe(probeIp, PROBE_HOST)).run { p ->
                    _state.value = DemoUiState(running = true, progress = p)
                }
                for (r in outcome.results) {
                    logger.i(
                        LogCategory.SYSTEM,
                        "demo: ${r.phase.name} probes ${r.stats.sent} lost ${r.stats.lost} median ${r.stats.medianMs} ms p95 ${r.stats.p95Ms} ms; " +
                            "download ${r.loadBytesPerSec * 8 / 1_000_000.0} Mbps" + (if (r.valid) "" else " (NOT VALID)") + (r.loadProblem?.let { "; $it" } ?: ""),
                    )
                }
                logger.i(LogCategory.SYSTEM, "demo: ${outcome.step.name.lowercase()}" + (outcome.message?.let { " ($it)" } ?: ""))
                _state.value = DemoUiState(running = false, progress = outcome)
            } catch (e: CancellationException) {
                logger.i(LogCategory.SYSTEM, "demo: cancelled")
                _state.value = DemoUiState(running = false, notStarted = "The demo was cancelled. Everything is back as before.")
                throw e
            } catch (t: Throwable) {
                logger.w(LogCategory.SYSTEM, "demo: failed (${t.javaClass.simpleName}: ${t.message})")
                _state.value = DemoUiState(running = false, notStarted = "The demo failed: ${t.message}")
            } finally {
                DemoGate.end() // whatever happened: the app is out of the tunnel again after the next restart
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun checkReady(): String? {
        val status = controller.status.value
        if (status.state == TunnelState.PAUSED) return "Protection is paused. Resume it first."
        if (status.state != TunnelState.RUNNING) return "Switch protection ON first (Home tab)."
        if (settingsRepository.current().schedRateDownBytesPerSec <= 0L) {
            return "The internet speed is not measured yet, so priority is not active. Wait a minute and try again."
        }
        return null
    }

    private suspend fun waitUntil(timeoutMs: Long, condition: (VpnStatus) -> Boolean): Boolean =
        withTimeoutOrNull(timeoutMs) { controller.status.first(condition) } != null

    private suspend fun restartAndWait(): Boolean {
        val before = controller.status.value.startedAtMillis
        controller.restart()
        return waitUntil(RESTART_TIMEOUT_MS) { it.state == TunnelState.RUNNING && it.startedAtMillis != before }
    }

    private fun controlsFor(ip: String) = object : DemoControls {
        override suspend fun enter(): Boolean {
            DemoGate.begin(setOf(ip))
            return restartAndWait()
        }

        override suspend fun pause(): Boolean {
            controller.pause()
            return waitUntil(PAUSE_TIMEOUT_MS) { it.state == TunnelState.PAUSED }
        }

        override suspend fun resume(): Boolean {
            controller.resume()
            return waitUntil(PAUSE_TIMEOUT_MS) { it.state == TunnelState.RUNNING }
        }

        override suspend fun leave() {
            DemoGate.end()
            val now = controller.status.value
            if (now.state == TunnelState.PAUSED) {
                // an engine that stays paused through a restart would pass everything unshaped: resume first
                controller.resume()
                waitUntil(PAUSE_TIMEOUT_MS) { it.state == TunnelState.RUNNING }
            }
            if (controller.status.value.isActive) restartAndWait()
        }
    }

    private fun loadGeneratorFor(download: ChosenDownload) = object : LoadGenerator {
        override fun start() {
            simulator.start(SimMode.DOWN, download.source.url, download.source.url, LOAD_AUTO_STOP_SEC, listOf(download.address))
        }

        override fun stop() {
            simulator.stop()
        }

        override fun totalBytes(): Long = simulator.stats.value.totalDown

        override fun problem(): String? {
            val s = simulator.stats.value
            return if (s.errors > 0) "${s.errors} download error(s), last: ${s.lastError}" else null
        }
    }

    private companion object {
        const val PROBE_HOST = "connectivitycheck.gstatic.com"
        const val RESTART_TIMEOUT_MS = 30_000L
        const val PAUSE_TIMEOUT_MS = 10_000L
        const val LOAD_AUTO_STOP_SEC = 60
    }
}
