package dev.netvalve.scheduler

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * What the relay carried per priority level since the app process started (patch B3).
 *
 * The relay counts every chunk it hands to the scheduler: bytes per level and direction, and (only
 * while the scheduler is active) how long the chunk waited for its turn. Everything here is a few
 * atomic adds and can never throw or block: an out-of-range level is clamped, a negative number
 * is ignored. A failure of the counters therefore cannot touch the traffic.
 *
 * Pure Kotlin, no Android imports.
 */
class LevelByteCounter(private val levels: Int = PriorityLevels.COUNT) {
    private val down = AtomicLongArray(levels)
    private val up = AtomicLongArray(levels)
    private val waitMsSum = AtomicLongArray(levels)
    private val waitCount = AtomicLongArray(levels)
    private val waitMax = AtomicLongArray(levels)
    private val unattributed = AtomicLong(0L)

    private fun index(level: Int): Int = if (level < 0 || level >= levels) levels - 1 else level

    /** [attributed] = false when the flow's app (UID) is not known; its bytes are counted as "normal" too. */
    fun addBytes(level: Int, lane: Lane, bytes: Int, attributed: Boolean) {
        if (bytes <= 0) return
        val i = index(level)
        if (lane == Lane.DOWNLOAD) down.addAndGet(i, bytes.toLong()) else up.addAndGet(i, bytes.toLong())
        if (!attributed) unattributed.addAndGet(bytes.toLong())
    }

    /** One chunk waited [ms] milliseconds in the scheduler (0 = it went through at once). */
    fun addWait(level: Int, ms: Long) {
        val i = index(level)
        val w = if (ms < 0L) 0L else ms
        waitMsSum.addAndGet(i, w)
        waitCount.incrementAndGet(i)
        while (true) {
            val cur = waitMax.get(i)
            if (w <= cur || waitMax.compareAndSet(i, cur, w)) break
        }
    }

    fun snapshot(): LevelTotals = LevelTotals(
        downBytes = List(levels) { down.get(it) },
        upBytes = List(levels) { up.get(it) },
        waitMsSum = List(levels) { waitMsSum.get(it) },
        waitCount = List(levels) { waitCount.get(it) },
        waitMaxMs = List(levels) { waitMax.get(it) },
        unattributedBytes = unattributed.get(),
    )
}

/** A copy of the counters at one moment; every list has one entry per level (index = level). */
data class LevelTotals(
    val downBytes: List<Long>,
    val upBytes: List<Long>,
    val waitMsSum: List<Long>,
    val waitCount: List<Long>,
    val waitMaxMs: List<Long>,
    val unattributedBytes: Long,
) {
    /**
     * What happened since [previous]. Sums and counts are subtracted (never below 0); the maximum
     * wait cannot be subtracted, so the delta carries the current maximum and the receiver keeps
     * the larger value.
     */
    fun deltaFrom(previous: LevelTotals): LevelTotals {
        fun sub(a: List<Long>, b: List<Long>) = a.mapIndexed { i, v -> (v - (b.getOrNull(i) ?: 0L)).coerceAtLeast(0L) }
        return LevelTotals(
            downBytes = sub(downBytes, previous.downBytes),
            upBytes = sub(upBytes, previous.upBytes),
            waitMsSum = sub(waitMsSum, previous.waitMsSum),
            waitCount = sub(waitCount, previous.waitCount),
            waitMaxMs = waitMaxMs,
            unattributedBytes = (unattributedBytes - previous.unattributedBytes).coerceAtLeast(0L),
        )
    }

    companion object {
        fun zero(levels: Int = PriorityLevels.COUNT): LevelTotals {
            val z = List(levels) { 0L }
            return LevelTotals(z, z, z, z, z, 0L)
        }
    }
}
