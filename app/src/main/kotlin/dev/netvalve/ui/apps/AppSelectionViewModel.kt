package dev.netvalve.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.netvalve.data.model.InstalledApp
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.repository.AppSelectionRepository
import dev.netvalve.repository.InstalledAppsRepository
import dev.netvalve.scheduler.PriorityLevels
import dev.netvalve.scheduler.PriorityOrder
import dev.netvalve.service.PriorityStore
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
    /** 1-based place in the priority list; 0 = not in the list ("normal"). */
    val rank: Int = 0,
    /** Fixed entry (NetValve itself = normal, PayProbe = most important); cannot be changed. */
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
    private val priorityStore: PriorityStore,
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
    }.combine(priorityStore.list) { state, _ ->
        // Re-read the rank of every row whenever the priority list changes (or the saved list loads).
        state.copy(
            apps = state.apps.map { row ->
                row.copy(
                    rank = priorityStore.rankOf(row.app.packageName),
                    locked = priorityStore.isLocked(row.app.packageName),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _tierMessage = MutableStateFlow<String?>(null)

    /** One line of feedback for the priority chips (for example when the list is full). */
    val tierMessage: StateFlow<String?> = _tierMessage.asStateFlow()

    /** Decides what a ticked checkbox means (controlled vs excluded). */
    val selectionMode: StateFlow<SelectionMode> = settingsRepository.settings
        .map { it.selectionMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectionMode.ONLY_SELECTED)

    fun setQuery(q: String) { query.value = q }

    /** Tap on the priority chip: adds the app at the end of the priority list, or takes it out again. */
    fun togglePriority(packageName: String) {
        val result = if (priorityStore.rankOf(packageName) > 0) {
            priorityStore.remove(packageName)
        } else {
            priorityStore.add(packageName)
        }
        _tierMessage.value = when (result) {
            PriorityOrder.Result.FULL ->
                "The priority list holds at most ${PriorityLevels.MAX_RANKS} apps. Remove one first."
            PriorityOrder.Result.LOCKED -> "This app's place is fixed."
            else -> null
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
