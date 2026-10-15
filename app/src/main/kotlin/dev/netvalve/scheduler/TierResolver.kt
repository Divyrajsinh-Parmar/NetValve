package dev.netvalve.scheduler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * uid -> tier for the relay hot path.
 *
 * A UID maps to one or more packages ([packagesForUid], Android's PackageManager in production,
 * a fake in tests); [TierAssignments] turns those into one tier (shared UID takes the highest).
 * Results are cached per UID because the relay asks for every chunk. Call [invalidate] whenever a
 * tier changes or a package is added or removed.
 *
 * Invalid (-1) and unattributed (-2) UIDs map to MEDIUM without asking the package source.
 * Pure Kotlin: no Android imports.
 */
class TierResolver(
    private val assignments: TierAssignments,
    private val packagesForUid: (Int) -> List<String>,
) {
    private val cache = ConcurrentHashMap<Int, Tier>()

    // Bumped by invalidate(): a lookup that started before an invalidate must not store its
    // (possibly outdated) answer afterwards.
    private val generation = AtomicInteger(0)

    fun tierOfUid(uid: Int): Tier {
        if (uid < 0) return Tier.MEDIUM
        cache[uid]?.let { return it }
        val startedAt = generation.get()
        val tier = assignments.tierOfPackages(packagesForUid(uid))
        if (generation.get() == startedAt) cache[uid] = tier
        return tier
    }

    fun invalidate() {
        generation.incrementAndGet()
        cache.clear()
    }
}
