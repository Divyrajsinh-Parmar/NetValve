package dev.netvalve.scheduler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * uid -> priority level for the relay hot path (the ranked counterpart of [TierResolver]).
 *
 * A UID maps to one or more packages ([packagesForUid]); [PriorityOrder] turns them into one level.
 * Results are cached per UID because the relay asks for every chunk. Call [invalidate] whenever the
 * list changes or a package is added or removed.
 *
 * Invalid (-1) and unattributed (-2) UIDs are "normal" ([PriorityLevels.NORMAL]) without asking the
 * package source. Pure Kotlin: no Android imports.
 */
class LevelResolver(
    private val order: PriorityOrder,
    private val packagesForUid: (Int) -> List<String>,
) {
    private val cache = ConcurrentHashMap<Int, Int>()

    // Bumped by invalidate(): a lookup that started before an invalidate must not store its
    // (possibly outdated) answer afterwards.
    private val generation = AtomicInteger(0)

    fun levelOfUid(uid: Int): Int {
        if (uid < 0) return PriorityLevels.NORMAL
        cache[uid]?.let { return it }
        val startedAt = generation.get()
        val level = order.levelOfPackages(packagesForUid(uid))
        if (generation.get() == startedAt) cache[uid] = level
        return level
    }

    fun invalidate() {
        generation.incrementAndGet()
        cache.clear()
    }
}
