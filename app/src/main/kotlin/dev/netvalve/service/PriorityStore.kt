package dev.netvalve.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.netvalve.di.AppScope
import dev.netvalve.repository.AppInfoLookup
import dev.netvalve.repository.PriorityOrderRepository
import dev.netvalve.scheduler.LevelResolver
import dev.netvalve.scheduler.PriorityLevels
import dev.netvalve.scheduler.PriorityOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide owner of the ordered priority list: the single object that the priority screen writes
 * and the relay reads (through [resolver]). It replaces [TierStore], which stays in the code but is
 * not used any more.
 *
 * Fixed entries: this app itself is always "normal" and PayProbe is always the most important level
 * ([PriorityLevels.PROBE]). `context.packageName` is used for the first so the ".debug" suffix of
 * debug builds is covered.
 *
 * The saved list loads asynchronously after start; until then every app is "normal" (the safe default).
 */
@Singleton
class PriorityStore @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: PriorityOrderRepository,
    appInfo: AppInfoLookup,
    @AppScope private val scope: CoroutineScope,
) {
    private val ownPackage: String = context.packageName

    private val order = PriorityOrder(
        locked = mapOf(ownPackage to PriorityLevels.NORMAL, PAYPROBE_PACKAGE to PriorityLevels.PROBE),
    )

    /** Used by the relay: uid -> priority level, cached. */
    val resolver = LevelResolver(order) { uid -> appInfo.packagesForUid(uid) }

    private val _list = MutableStateFlow<List<String>>(emptyList())

    /** The priority list, most important app first. */
    val list: StateFlow<List<String>> = _list

    init {
        scope.launch {
            val saved = runCatching { repository.current() }.getOrDefault(emptyList())
            saved.forEach { order.add(it) }
            resolver.invalidate()
            _list.value = order.snapshot()
        }
    }

    /** 1-based place in the list, or 0 when the app is not in it. */
    fun rankOf(packageName: String): Int = order.rankOf(packageName)

    fun isLocked(packageName: String): Boolean = order.isLocked(packageName)

    /** Adds the app at the end of the list. Applies at once to running flows; persists in the background. */
    fun add(packageName: String): PriorityOrder.Result = changed(order.add(packageName))

    fun remove(packageName: String): PriorityOrder.Result = changed(order.remove(packageName))

    fun moveUp(packageName: String): PriorityOrder.Result = changed(order.moveUp(packageName))

    fun moveDown(packageName: String): PriorityOrder.Result = changed(order.moveDown(packageName))

    private fun changed(result: PriorityOrder.Result): PriorityOrder.Result {
        if (result == PriorityOrder.Result.OK) {
            resolver.invalidate()
            val snapshot = order.snapshot()
            _list.value = snapshot
            scope.launch { runCatching { repository.save(snapshot) } }
        }
        return result
    }

    companion object {
        const val PAYPROBE_PACKAGE = "dev.netvalve.payprobe"
    }
}
