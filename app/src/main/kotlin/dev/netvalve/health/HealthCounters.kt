package dev.netvalve.health

import dev.netvalve.scheduler.LevelTotals
import dev.netvalve.scheduler.PriorityLevels

/**
 * The long-term numbers behind the Stats tab (patch B3): kept across app restarts and days.
 *
 * Every number says what it counts:
 *  - [runningMs]: time the tunnel was up (protection ON or Paused), summed over all runs.
 *  - [longestRunMs]: the longest single run of the tunnel (a tunnel restart starts a new run).
 *  - [tunnelStarts]: how often the tunnel came up; [appStarts]: how often the app process started.
 *  - failures: [establishFailures] (the VPN could not be set up), [engineErrors] (other ERROR lines of
 *    the log), [autoSpeedFailures] (a speed measurement failed); [connectFailures] are connections of
 *    apps that could not be opened (for example to an unreachable address): not a fault of the scheduler.
 *  - per level (index = level, see [PriorityLevels]): bytes down/up, and the wait of chunks in the
 *    scheduler (only counted while the scheduler is active).
 *
 * Pure Kotlin. Stored as plain text lines ([encode] / [decode]); a damaged value reads as empty.
 */
data class HealthCounters(
    val sinceMs: Long = 0L,
    val appStarts: Int = 0,
    val tunnelStarts: Int = 0,
    val runningMs: Long = 0L,
    val longestRunMs: Long = 0L,
    val establishFailures: Int = 0,
    val engineErrors: Int = 0,
    val autoSpeedFailures: Int = 0,
    val connectFailures: Int = 0,
    val lastFailureAtMs: Long = 0L,
    val downBytes: List<Long> = ZEROS,
    val upBytes: List<Long> = ZEROS,
    val waitMsSum: List<Long> = ZEROS,
    val waitCount: List<Long> = ZEROS,
    val waitMaxMs: List<Long> = ZEROS,
    val unattributedBytes: Long = 0L,
) {
    /** Adds what the relay carried since the last call (see [LevelTotals.deltaFrom]). */
    fun plusLevels(delta: LevelTotals): HealthCounters {
        fun add(a: List<Long>, b: List<Long>) = List(LEVELS) { (a.getOrElse(it) { 0L }) + (b.getOrElse(it) { 0L }) }
        return copy(
            downBytes = add(downBytes, delta.downBytes),
            upBytes = add(upBytes, delta.upBytes),
            waitMsSum = add(waitMsSum, delta.waitMsSum),
            waitCount = add(waitCount, delta.waitCount),
            waitMaxMs = List(LEVELS) { maxOf(waitMaxMs.getOrElse(it) { 0L }, delta.waitMaxMs.getOrElse(it) { 0L }) },
            unattributedBytes = unattributedBytes + delta.unattributedBytes,
        )
    }

    fun totalFailures(): Int = establishFailures + engineErrors + autoSpeedFailures

    /** Bytes (down + up) of the user's priority apps (levels 1 to 5), or of the normal level. */
    fun priorityBytes(): Long = (PriorityLevels.FIRST_RANK until PriorityLevels.NORMAL).sumOf { level(downBytes, it) + level(upBytes, it) }
    fun normalBytes(): Long = level(downBytes, PriorityLevels.NORMAL) + level(upBytes, PriorityLevels.NORMAL)
    fun probeBytes(): Long = level(downBytes, PriorityLevels.PROBE) + level(upBytes, PriorityLevels.PROBE)
    fun allBytes(): Long = downBytes.sum() + upBytes.sum()

    /** Average wait (ms) of chunks of the priority apps in the scheduler, or null when none was measured. */
    fun priorityAvgWaitMs(): Double? = avgWait(PriorityLevels.FIRST_RANK until PriorityLevels.NORMAL)
    fun normalAvgWaitMs(): Double? = avgWait(PriorityLevels.NORMAL..PriorityLevels.NORMAL)
    fun priorityWaitChunks(): Long = (PriorityLevels.FIRST_RANK until PriorityLevels.NORMAL).sumOf { level(waitCount, it) }

    fun priorityMaxWaitMs(): Long = (PriorityLevels.FIRST_RANK until PriorityLevels.NORMAL).maxOf { level(waitMaxMs, it) }
    fun normalMaxWaitMs(): Long = level(waitMaxMs, PriorityLevels.NORMAL)

    /** Share (0..100) of all bytes that belong to an app (UID) that was recognised; null when no bytes. */
    fun attributedPercent(): Double? {
        val all = allBytes()
        if (all <= 0L) return null
        return 100.0 * (all - unattributedBytes).coerceAtLeast(0L) / all
    }

    private fun avgWait(range: IntRange): Double? {
        val n = range.sumOf { level(waitCount, it) }
        if (n <= 0L) return null
        return range.sumOf { level(waitMsSum, it) }.toDouble() / n
    }

    private fun level(list: List<Long>, i: Int): Long = list.getOrElse(i) { 0L }

    fun encode(): String = buildString {
        append("v1\n")
        append("since=").append(sinceMs).append('\n')
        append("appStarts=").append(appStarts).append('\n')
        append("tunnelStarts=").append(tunnelStarts).append('\n')
        append("runningMs=").append(runningMs).append('\n')
        append("longestRunMs=").append(longestRunMs).append('\n')
        append("establishFailures=").append(establishFailures).append('\n')
        append("engineErrors=").append(engineErrors).append('\n')
        append("autoSpeedFailures=").append(autoSpeedFailures).append('\n')
        append("connectFailures=").append(connectFailures).append('\n')
        append("lastFailureAt=").append(lastFailureAtMs).append('\n')
        append("down=").append(downBytes.joinToString(",")).append('\n')
        append("up=").append(upBytes.joinToString(",")).append('\n')
        append("waitMsSum=").append(waitMsSum.joinToString(",")).append('\n')
        append("waitCount=").append(waitCount.joinToString(",")).append('\n')
        append("waitMax=").append(waitMaxMs.joinToString(",")).append('\n')
        append("unattributed=").append(unattributedBytes).append('\n')
    }

    companion object {
        const val LEVELS = PriorityLevels.COUNT
        val ZEROS: List<Long> = List(LEVELS) { 0L }

        /** Reads what [encode] wrote. Anything unknown or damaged is skipped; a missing value is 0. */
        fun decode(raw: String?): HealthCounters {
            if (raw.isNullOrBlank()) return HealthCounters()
            val m = HashMap<String, String>()
            for (line in raw.lineSequence()) {
                val at = line.indexOf('=')
                if (at > 0) m[line.substring(0, at)] = line.substring(at + 1).trim()
            }
            fun long(key: String): Long = m[key]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            fun int(key: String): Int = m[key]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            fun list(key: String): List<Long> {
                val parts = m[key]?.split(',') ?: emptyList()
                return List(LEVELS) { parts.getOrNull(it)?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L }
            }
            return HealthCounters(
                sinceMs = long("since"),
                appStarts = int("appStarts"),
                tunnelStarts = int("tunnelStarts"),
                runningMs = long("runningMs"),
                longestRunMs = long("longestRunMs"),
                establishFailures = int("establishFailures"),
                engineErrors = int("engineErrors"),
                autoSpeedFailures = int("autoSpeedFailures"),
                connectFailures = int("connectFailures"),
                lastFailureAtMs = long("lastFailureAt"),
                downBytes = list("down"),
                upBytes = list("up"),
                waitMsSum = list("waitMsSum"),
                waitCount = list("waitCount"),
                waitMaxMs = list("waitMax"),
                unattributedBytes = long("unattributed"),
            )
        }
    }
}
