package dev.netvalve.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.netvalve.data.model.InstalledApp
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.repository.AppSelectionRepository
import dev.netvalve.repository.InstalledAppsRepository
import dev.netvalve.scheduler.Tier
import dev.netvalve.scheduler.TierAssignments
import dev.netvalve.service.TierStore
import dev.netvalve.service.VpnController
import dev.netvalve.service.VpnStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRowUi(
    val app: InstalledApp,
    val selected: Boolean,
    /** Priority tier (M4). Only meaningful for apps the tunnel controls. */
    val tier: Tier = Tier.MEDIUM,
    /** Fixed tier (NetValve itself = Low, PayProbe = High); cannot be changed. */
    val locked: Boolean = false,
)

data class AppsUiState(
    val apps: List<AppRowUi> = emptyList(),
    val query: String = "",
    val includeSystem: Boolean = false,
    val loading: Boolean = true,
    val pendingRestart: Boolean = false,
    val tunnelActive: Boolean = false,
)

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val installedApps: InstalledAppsRepository,
    private val selectionRepository: AppSelectionRepository,
    private val controller: VpnController,
    private val tierStore: TierStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val includeSystem = MutableStateFlow(false)
    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val pendingRestart = MutableStateFlow(false)

    init { reload() }

    val uiState: StateFlow<AppsUiState> = combine(
        allApps, selectionRepository.selectedPackages, query, includeSystem, loading,
    ) { apps, selected, q, _, isLoading ->
        val filtered = apps
            .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .map { AppRowUi(it, it.packageName in selected) }
        Triple(filtered, isLoading, selected)
    }.combine(controller.status) { triple, status ->
        AppsUiState(
            apps = triple.first,
            query = query.value,
            includeSystem = includeSystem.value,
            loading = triple.second,
            pendingRestart = pendingRestart.value && status.isActive,
            tunnelActive = status.isActive,
        )
    }.combine(tierStore.tiers) { state, _ ->
        // Re-read the tier of every row whenever an assignment changes (or the saved ones load).
        state.copy(
            apps = state.apps.map { row ->
                row.copy(
                    tier = tierStore.tierOfPackage(row.app.packageName),
                    locked = tierStore.isLocked(row.app.packageName),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _tierMessage = MutableStateFlow<String?>(null)

    /** One line of feedback for the tier chips (for example when High is full). */
    val tierMessage: StateFlow<String?> = _tierMessage.asStateFlow()

    /** Decides what a ticked checkbox means (controlled vs excluded). */
    val selectionMode: StateFlow<SelectionMode> = settingsRepository.settings
        .map { it.selectionMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectionMode.ONLY_SELECTED)

    fun setQuery(q: String) { query.value = q }

    /** Tap on a tier chip: Medium -> High -> Low -> Medium. High holds at most 3 apps. */
    fun cycleTier(packageName: String) {
        val next = when (tierStore.tierOfPackage(packageName)) {
            Tier.MEDIUM -> Tier.HIGH
            Tier.HIGH -> Tier.LOW
            Tier.LOW -> Tier.MEDIUM
        }
        when (tierStore.set(packageName, next)) {
            TierAssignments.SetResult.OK -> _tierMessage.value = null
            TierAssignments.SetResult.HIGH_FULL -> {
                tierStore.set(packageName, Tier.LOW)
                _tierMessage.value = "High holds at most ${Tier.MAX_HIGH_APPS} apps, so this app went to Low. " +
                    "Move a High app to Low or Medium first to free a place."
            }
            TierAssignments.SetResult.LOCKED -> _tierMessage.value = "This app's tier is fixed."
        }
    }

    fun setIncludeSystem(value: Boolean) {
        includeSystem.value = value
        reload()
    }

    fun toggle(packageName: String, selected: Boolean) {
        if (controller.status.value.isActive) pendingRestart.value = true
        viewModelScope.launch { selectionRepository.setSelected(packageName, selected) }
    }

    /** Tick or untick every app currently shown (respects the search filter). */
    fun setAllShown(selected: Boolean) {
        val shown = uiState.value.apps.map { it.app.packageName }
        if (controller.status.value.isActive) pendingRestart.value = true
        viewModelScope.launch { shown.forEach { selectionRepository.setSelected(it, selected) } }
    }

    /** Rebuild the tunnel so the new allow/deny list takes effect. */
    fun applyChanges() {
        controller.restart()
        pendingRestart.value = false
    }

    private fun reload() {
        viewModelScope.launch {
            loading.value = true
            allApps.value = installedApps.listUserApps(includeSystem = includeSystem.value)
            loading.value = false
        }
    }
}
