package dev.netvalve.scheduler

/**
 * The strict priority levels of the ranked scheduler (smaller number = more important).
 *
 *  - [PROBE] (0): reserved for the measuring app PayProbe (locked, never in the user's list).
 *  - [FIRST_RANK] .. [FIRST_RANK] + [MAX_RANKS] - 1: the user's priority apps, in the order of the
 *    priority list (first app of the list = level 1).
 *  - [NORMAL]: every other app (the "normal container"). Always the last level.
 *
 * Pure Kotlin, no Android imports.
 */
object PriorityLevels {
    /** The user's priority list holds at most this many apps. */
    const val MAX_RANKS = 5

    const val PROBE = 0
    const val FIRST_RANK = 1
    const val NORMAL = FIRST_RANK + MAX_RANKS // 6
    const val COUNT = NORMAL + 1 // 7
}
