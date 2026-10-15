package dev.netvalve.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.netvalve.di.AppScope
import dev.netvalve.repository.AppInfoLookup
import dev.netvalve.repository.TierRepository
import dev.netvalve.scheduler.Tier
import dev.netvalve.scheduler.TierAssignments
import dev.netvalve.scheduler.TierResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide owner of the tier assignments (roadmap M4): the single object that the tier UI writes
 * and the relay reads (through [resolver]).
 *
 * Fixed entries: this app itself is always LOW (the in-app traffic simulator is the "Low app" of
 * the evaluation) and PayProbe is always HIGH. `context.packageName` is used for the first so the
 * ".debug" suffix of debug builds is covered.
 *
 * Saved tiers load asynchronously after start; until then every app is MEDIUM (the safe default).
 */
@Singleton
class TierStore @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: TierRepository,
    appInfo: AppInfoLookup,
    @AppScope private val scope: CoroutineScope,
) {
    private val ownPackage: String = context.packageName

    private val assignments = TierAssignments(
        locked = mapOf(ownPackage to Tier.LOW, PAYPROBE_PACKAGE to Tier.HIGH),
    )

    /** Used by the relay: uid -> tier, cached. */
    val resolver = TierResolver(assignments) { uid -> appInfo.packagesForUid(uid) }

    private val _tiers = MutableStateFlow<Map<String, Tier>>(emptyMap())

    /** The explicit (non-default) assignments; fixed entries are not in here, see [tierOfPackage]. */
    val tiers: StateFlow<Map<String, Tier>> = _tiers

    init {
        scope.launch {
            val saved = runCatching { repository.current() }.getOrDefault(emptyMap())
            saved.forEach { (pkg, tier) -> assignments.set(pkg, tier) }
            resolver.invalidate()
            _tiers.value = assignments.snapshot()
        }
    }

    fun tierOfPackage(packageName: String): Tier = assignments.tierOf(packageName)

    fun isLocked(packageName: String): Boolean = packageName == ownPackage || packageName == PAYPROBE_PACKAGE

    /** Applies at once to running flows (the relay asks the resolver per chunk); persists in the background. */
    fun set(packageName: String, tier: Tier): TierAssignments.SetResult {
        val result = assignments.set(packageName, tier)
        if (result == TierAssignments.SetResult.OK) {
            resolver.invalidate()
            val snapshot = assignments.snapshot()
            _tiers.value = snapshot
            scope.launch { runCatching { repository.save(snapshot) } }
        }
        return result
    }

    companion object {
        const val PAYPROBE_PACKAGE = "dev.netvalve.payprobe"
    }
}
