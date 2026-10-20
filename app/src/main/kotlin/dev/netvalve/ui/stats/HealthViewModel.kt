package dev.netvalve.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.netvalve.calibration.CalibrationRepository
import dev.netvalve.health.HealthCounters
import dev.netvalve.health.HealthRecorder
import dev.netvalve.health.HealthReport
import dev.netvalve.health.HealthRules
import dev.netvalve.health.HealthVerdict
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.service.PriorityStore
import dev.netvalve.service.TunnelState
import dev.netvalve.service.VpnController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HealthUiState(
    val counters: HealthCounters = HealthCounters(),
    val verdict: HealthVerdict = HealthRules.evaluate(false, false, 0, 0L, HealthCounters(), 0L),
    /** Last measured shared speed per network type, in bytes/s (null = none yet). */
    val speeds: List<Pair<String, Long?>> = emptyList(),
    val nowMs: Long = 0L,
)

/** Backs the simple Stats tab (patch B3). Read-only: nothing here can change traffic. */
@HiltViewModel
class HealthViewModel @Inject constructor(
    recorder: HealthRecorder,
    controller: VpnController,
    settingsRepository: SettingsRepository,
    priorityStore: PriorityStore,
    private val calibration: CalibrationRepository,
) : ViewModel() {

    private val speedsFlow = recorder.counters.map { readSpeeds() }

    val uiState: StateFlow<HealthUiState> = combine(
        recorder.counters,
        controller.status,
        settingsRepository.settings,
        priorityStore.list,
        speedsFlow,
    ) { counters, status, settings, priority, speeds ->
        val now = System.currentTimeMillis()
        val running = status.state == TunnelState.RUNNING || status.state == TunnelState.PAUSED
        val verdict = HealthRules.evaluate(
            running = running,
            paused = status.state == TunnelState.PAUSED,
            priorityApps = priority.size,
            rateDownBytesPerSec = settings.schedRateDownBytesPerSec,
            c = counters,
            nowMs = now,
        )
        HealthUiState(counters, verdict, speeds, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    /** Plain-text copy of the summary for the Share button. */
    fun reportText(): String {
        val s = uiState.value
        return HealthReport.text(s.counters, s.verdict, s.speeds, System.currentTimeMillis())
    }

    private suspend fun readSpeeds(): List<Pair<String, Long?>> =
        listOf("Wi-Fi" to "WIFI", "Mobile data" to "MOBILE", "Ethernet" to "ETHERNET").map { (label, key) ->
            label to runCatching { calibration.get(key)?.bytesPerSec }.getOrNull()
        }
}
