package dev.netvalve.ui.sim

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.netvalve.sim.SimMode
import dev.netvalve.sim.SimStats
import dev.netvalve.sim.TrafficSimulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SimulatorViewModel @Inject constructor(
    private val simulator: TrafficSimulator,
) : ViewModel() {

    val stats: StateFlow<SimStats> = simulator.stats

    private val _error = MutableStateFlow<String?>(null)

    /** Shown when a URL or the auto-stop value is invalid; nothing is started then. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(mode: SimMode, downloadUrl: String, uploadUrl: String, autoStopText: String) {
        val autoStop = autoStopText.trim().toIntOrNull()
        if (autoStop == null || autoStop < 0) {
            _error.value = "Auto-stop must be a whole number of seconds (0 = never)."
            return
        }
        try {
            simulator.start(mode, downloadUrl, uploadUrl, autoStop)
            _error.value = null
        } catch (e: IllegalArgumentException) {
            _error.value = e.message ?: "Invalid URL."
        }
    }

    fun stop() = simulator.stop()
}
