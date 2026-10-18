package dev.netvalve.ui.priority

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.netvalve.data.model.InstalledApp
import dev.netvalve.repository.InstalledAppsRepository
import dev.netvalve.scheduler.PriorityLevels
import dev.netvalve.scheduler.PriorityOrder
import dev.netvalve.service.PriorityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriorityRowUi(
    val packageName: String,
    val label: String,
    /** 1 = most important. */
    val rank: Int,
)

data class PriorityUiState(
    val priority: List<PriorityRowUi> = emptyList(),
    /** Installed apps that are not in the priority list (they are "normal"), filtered by the search text. */
    val others: List<InstalledApp> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val message: String? = null,
    val isFull: Boolean = false,
)

/** Backs the Apps tab: the ordered priority list and the list of all other apps. */
@HiltViewModel
class PriorityViewModel @Inject constructor(
    private val installedApps: InstalledAppsRepository,
    private val priorityStore: PriorityStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            allApps.value = installedApps.listUserApps(includeSystem = false)
            loading.value = false
        }
    }

    val uiState: StateFlow<PriorityUiState> = combine(
        priorityStore.list, allApps, query, loading, message,
    ) { list, apps, q, isLoading, msg ->
        val rows = list.mapIndexed { i, pkg -> PriorityRowUi(pkg, installedApps.labelForPackage(pkg), i + 1) }
        val inList = list.toSet()
        val others = apps
            .filter { it.packageName !in inList && !priorityStore.isLocked(it.packageName) }
            .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
        PriorityUiState(
            priority = rows,
            others = others,
            query = q,
            loading = isLoading,
            message = msg,
            isFull = list.size >= PriorityLevels.MAX_RANKS,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriorityUiState())

    fun setQuery(q: String) { query.value = q }

    fun add(packageName: String) = report(priorityStore.add(packageName))
    fun remove(packageName: String) = report(priorityStore.remove(packageName))
    fun moveUp(packageName: String) = report(priorityStore.moveUp(packageName))
    fun moveDown(packageName: String) = report(priorityStore.moveDown(packageName))

    private fun report(result: PriorityOrder.Result) {
        message.value = when (result) {
            PriorityOrder.Result.FULL ->
                "The list is full (${PriorityLevels.MAX_RANKS} apps). Remove one first."
            PriorityOrder.Result.LOCKED -> "This app cannot be changed."
            else -> null
        }
    }
}
