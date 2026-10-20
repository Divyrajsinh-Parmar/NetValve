package dev.netvalve.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.netvalve.data.model.AppRule
import dev.netvalve.data.model.GlobalSettings
import dev.netvalve.data.model.Ipv6Mode
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.repository.AppInfoLookup
import dev.netvalve.repository.AppSelectionRepository
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.scheduler.RateUnits
import dev.netvalve.scheduler.SchedMode
import dev.netvalve.service.PriorityStore
import dev.netvalve.rules.DeviceState
import dev.netvalve.rules.DeviceStateMonitor
import dev.netvalve.service.BatteryOptimizations
import dev.netvalve.service.TunnelState
import dev.netvalve.service.UsageAccess
import dev.netvalve.service.VpnController
import dev.netvalve.service.VpnStatus
import dev.netvalve.stats.StatsCollector
import dev.netvalve.stats.StatsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class ControlledAppRow(
    val packageName: String,
    val label: String,
    val rule: AppRule?,
    /** 1-based place in the priority list; 0 = not in the list ("normal"). */
    val rank: Int = 0,
)

data class DashboardUiState(
    val status: VpnStatus = VpnStatus(),
    val stats: StatsSnapshot = StatsSnapshot(),
    val settings: GlobalSettings = GlobalSettings.Default,
    val selectedCount: Int = 0,
    val controlledApps: List<ControlledAppRow> = emptyList(),
    val activeRuleCount: Int = 0,
    val device: DeviceState = DeviceState(),
    val ignoringBatteryOptimizations: Boolean = true,
    /** True when NetValve itself is in the ticked list (needed for the Load tab test). */
    val ownAppTicked: Boolean = true,
    /** Real state of the usage-access permission, read on the Home screen itself (the device monitor only runs while the tunnel is on). */
    val usageAccessGranted: Boolean = true,
) {
    val running: Boolean get() = status.isActive
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: VpnController,
    private val settingsRepository: SettingsRepository,
    private val selectionRepository: AppSelectionRepository,
    private val statsCollector: StatsCollector,
    private val appInfo: AppInfoLookup,
    private val priorityStore: PriorityStore,
    deviceStateMonitor: DeviceStateMonitor,
) : ViewModel() {

    private val partial = combine(
        controller.status,
        statsCollector.snapshot,
        selectionRepository.selectedPackages,
        settingsRepository.settings,
        deviceStateMonitor.state,
    ) { status, stats, selected, settings, device ->
        Partial(status, stats, selected, settings, device)
    }

    /** Ticks every 2 s while the Home screen is shown, so permission changes made in Android settings show up soon after returning. */
    private val permissionTick = flow {
        while (true) {
            emit(Unit)
            delay(2_000)
        }
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(partial, settingsRepository.rules, permissionTick) { p, rules, _ ->
            val controlled = p.selected.sorted().map { pkg ->
                ControlledAppRow(pkg, appInfo.labelForPackage(pkg), rules[pkg])
            }
            DashboardUiState(
                status = p.status,
                stats = p.stats,
                settings = p.settings,
                selectedCount = p.selected.size,
                controlledApps = controlled,
                activeRuleCount = rules.values.count { it.isActive },
                device = p.device,
                ignoringBatteryOptimizations = BatteryOptimizations.isIgnoringBatteryOptimizations(context),
                ownAppTicked = p.selected.contains(context.packageName),
                usageAccessGranted = UsageAccess.isGranted(context),
            )
        }.combine(priorityStore.list) { state, _ ->
            // Re-read the ranks whenever the priority list changes (or the saved list finishes loading).
            state.copy(
                controlledApps = state.controlledApps.map { it.copy(rank = priorityStore.rankOf(it.packageName)) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun pauseResume() {
        if (uiState.value.status.state == TunnelState.PAUSED) controller.resume() else controller.pause()
    }

    /**
     * Settings of the production-stage app, used by the simple Home screen: every app goes through
     * the tunnel except the ticked ones (NetValve itself is always left out), and Priority Control is
     * on, and the tunnel starts again after a phone restart if protection was on before it. Applied once per
     * app start, and only when something differs; a running tunnel is rebuilt when its app set changed.
     * The shared speed (R) is NOT touched here: 0 means "not scheduled", so nothing is slowed down
     * until a speed is set (by hand in Test tools now, automatically in patch B).
     */
    fun applyProductionDefaults() {
        if (!ProductionDefaults.claim()) return
        viewModelScope.launch {
            val before = settingsRepository.current()
            val tunnelChange = before.selectionMode != SelectionMode.ALL_EXCEPT ||
                before.schedMode != SchedMode.PRIORITY_PACER
            if (tunnelChange || !before.autoStartOnBoot) {
                settingsRepository.updateSettings {
                    it.copy(
                        selectionMode = SelectionMode.ALL_EXCEPT,
                        schedMode = SchedMode.PRIORITY_PACER,
                        autoStartOnBoot = true,
                    )
                }
                // Only a changed tunnel set needs a rebuild; the boot flag takes effect at the next boot.
                if (tunnelChange && uiState.value.running) controller.restart()
            }
        }
    }

    fun setSelectionMode(mode: SelectionMode) = update { it.copy(selectionMode = mode) }
    fun setIpv6Mode(mode: Ipv6Mode) = update { it.copy(ipv6Mode = mode) }
    fun setExemptDns(value: Boolean) = update { it.copy(exemptDns = value) }
    fun setAutoStart(value: Boolean) = update { it.copy(autoStartOnBoot = value) }

    /** Priority Control on/off (technical name: priority pacer, Mode B). Takes effect immediately; no tunnel restart needed. */
    fun setSchedEnabled(enabled: Boolean) =
        update { it.copy(schedMode = if (enabled) SchedMode.PRIORITY_PACER else SchedMode.OFF) }

    /** Shared speed (the pacer rate R) in Mbps; 0 = that direction is not scheduled. */
    fun setSchedRates(downMbps: Double, upMbps: Double) = update {
        it.copy(
            schedRateDownBytesPerSec = RateUnits.mbpsToBytesPerSec(downMbps),
            schedRateUpBytesPerSec = RateUnits.mbpsToBytesPerSec(upMbps),
        )
    }

    /** Applying a mode change while running needs a tunnel rebuild. */
    fun applyAndMaybeRestart() {
        if (uiState.value.running) controller.restart()
    }

    private fun update(transform: (GlobalSettings) -> GlobalSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    private data class Partial(
        val status: VpnStatus,
        val stats: StatsSnapshot,
        val selected: Set<String>,
        val settings: GlobalSettings,
        val device: DeviceState,
    )
}

/** Makes sure [DashboardViewModel.applyProductionDefaults] runs only once per app start. */
internal object ProductionDefaults {
    private val applied = AtomicBoolean(false)
    fun claim(): Boolean = applied.compareAndSet(false, true)
}
