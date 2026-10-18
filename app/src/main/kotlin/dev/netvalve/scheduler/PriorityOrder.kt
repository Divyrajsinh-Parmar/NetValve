package dev.netvalve.scheduler

/**
 * The user's ordered priority list (the "priority container") and the level of every package.
 *
 *  - The first package of the list is the most important and gets [PriorityLevels.FIRST_RANK],
 *    the second the next level, and so on. The list holds at most [PriorityLevels.MAX_RANKS] apps.
 *  - Every package that is not in the list is "normal" ([PriorityLevels.NORMAL]), the last level.
 *  - Locked packages have a fixed level and can neither be added, removed nor moved (PayProbe is
 *    locked to [PriorityLevels.PROBE], the app itself to [PriorityLevels.NORMAL]).
 *  - A shared-UID group (several packages, one UID) takes the MOST important level among its packages.
 *
 * Pure Kotlin so the UI and the relay can share it. Methods are @Synchronized: relay threads read
 * while the UI thread writes.
 */
class PriorityOrder(
    initial: List<String> = emptyList(),
    private val locked: Map<String, Int> = emptyMap(),
) {
    enum class Result { OK, FULL, LOCKED, ALREADY_IN_LIST, NOT_IN_LIST, AT_EDGE }

    private val list = ArrayList<String>().apply {
        for (pkg in initial) {
            if (pkg !in locked && pkg !in this && size < PriorityLevels.MAX_RANKS) add(pkg)
        }
    }

    @Synchronized fun levelOf(packageName: String): Int {
        locked[packageName]?.let { return it }
        val i = list.indexOf(packageName)
        return if (i < 0) PriorityLevels.NORMAL else PriorityLevels.FIRST_RANK + i
    }

    /** Level of a UID that maps to one or more packages (smallest number = most important). */
    @Synchronized fun levelOfPackages(packages: Collection<String>): Int =
        if (packages.isEmpty()) PriorityLevels.NORMAL else packages.minOf { levelOf(it) }

    /** 1-based place in the list, or 0 when the package is not in the list. */
    @Synchronized fun rankOf(packageName: String): Int = list.indexOf(packageName) + 1

    @Synchronized fun isLocked(packageName: String): Boolean = packageName in locked

    @Synchronized fun contains(packageName: String): Boolean = packageName in list

    @Synchronized fun size(): Int = list.size

    /** The list from the most important app to the least important one. */
    @Synchronized fun snapshot(): List<String> = list.toList()

    /** Adds the app at the END of the list (least important place of the list). */
    @Synchronized fun add(packageName: String): Result {
        if (packageName in locked) return Result.LOCKED
        if (packageName in list) return Result.ALREADY_IN_LIST
        if (list.size >= PriorityLevels.MAX_RANKS) return Result.FULL
        list.add(packageName)
        return Result.OK
    }

    @Synchronized fun remove(packageName: String): Result {
        if (packageName in locked) return Result.LOCKED
        return if (list.remove(packageName)) Result.OK else Result.NOT_IN_LIST
    }

    /** One place towards the front (more important). */
    @Synchronized fun moveUp(packageName: String): Result = shift(packageName, -1)

    /** One place towards the end (less important). */
    @Synchronized fun moveDown(packageName: String): Result = shift(packageName, +1)

    private fun shift(packageName: String, delta: Int): Result {
        if (packageName in locked) return Result.LOCKED
        val i = list.indexOf(packageName)
        if (i < 0) return Result.NOT_IN_LIST
        val j = i + delta
        if (j < 0 || j >= list.size) return Result.AT_EDGE
        val other = list[j]
        list[j] = list[i]
        list[i] = other
        return Result.OK
    }
}
