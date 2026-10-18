package dev.netvalve.scheduler

import kotlin.math.max
import kotlin.math.min

/**
 * Ranked version of [PriorityPacerCore]: [levels] strict priority levels instead of three tiers
 * (level 0 = most important). Same idea, same style (deterministic, no clock, no sleeping), so it
 * runs under virtual time in unit tests; [RankedPacer] turns grants into resumed `suspend` calls.
 *
 * Per tick:
 *  1. budget = min(carry + R * dt, 2 * R * tick)                       (banked budget is capped)
 *  2. floors: every level except 0 first gets floor * R * dt while it waits (no starvation);
 *     the last level uses [SchedulerConfig.floorLow], the levels in between [SchedulerConfig.floorMedium]
 *  3. strict priority for the rest: level 0, then 1, 2, ... (round-robin across flows inside a level,
 *     at most [SchedulerConfig.grantBytes] per turn)
 *  4. while ANY more important level is active (it has waiting requests, or moved bytes within the
 *     hold window) a level is capped: the last level at alphaLow * R, the levels in between at
 *     alphaMedium * R. When every more important level is idle the level has no cap (work-conserving)
 *  5. leftover budget is carried, but never beyond the cap of step 1
 *
 * With three levels this is the same rule as [PriorityPacerCore] except for one point: the last
 * level is also capped while the middle level is active (in [PriorityPacerCore] only High caps).
 *
 * Not thread-safe: the owner must serialise calls (one pump coroutine per direction).
 */
class RankedPacerCore<T>(val levels: Int, initial: SchedulerConfig) {

    /** Bytes that may proceed for one flow in this tick. [completed] means its request is fully served. */
    class Grant<T>(
        val level: Int,
        val flowId: Long,
        val bytes: Int,
        val token: T,
        val completed: Boolean,
    )

    private class Req<T>(val token: T, var remaining: Int)

    private class FlowQueue<T>(val flowId: Long) {
        val reqs = ArrayDeque<Req<T>>()
    }

    init {
        require(levels >= 2) { "need at least 2 levels" }
        checkFloors(initial)
    }

    var config: SchedulerConfig = initial
        private set

    private val order = Array(levels) { ArrayDeque<FlowQueue<T>>() }
    private val index = Array(levels) { HashMap<Long, FlowQueue<T>>() }

    private var lastTickMs = Long.MIN_VALUE
    private var carry = 0.0
    private val floorCredit = DoubleArray(levels)
    private val capCredit = DoubleArray(levels)
    private val capsWereOn = BooleanArray(levels)
    private val lastActiveMs = LongArray(levels) { NEVER }

    private val last = levels - 1

    /** Change rate or parameters in place (calibration finished, user edited a value). */
    fun updateConfig(newConfig: SchedulerConfig) {
        checkFloors(newConfig)
        config = newConfig
    }

    private fun checkFloors(c: SchedulerConfig) {
        val total = (levels - 2) * c.floorMedium + c.floorLow
        require(total < 1.0) { "floors of all levels must leave room for level 0 (sum=$total)" }
    }

    private fun capShare(level: Int, c: SchedulerConfig): Double =
        if (level == last) c.alphaLow else c.alphaMedium

    private fun floorShare(level: Int, c: SchedulerConfig): Double =
        if (level == last) c.floorLow else c.floorMedium

    private fun checkLevel(level: Int) {
        require(level in 0 until levels) { "level $level is outside 0..${levels - 1}" }
    }

    /** A flow asks to move [bytes] bytes. The same [flowId] should always use the same level. */
    fun submit(level: Int, flowId: Long, bytes: Int, token: T, nowMs: Long) {
        require(bytes > 0) { "bytes must be > 0" }
        checkLevel(level)
        val fq = index[level].getOrPut(flowId) {
            FlowQueue<T>(flowId).also { order[level].addLast(it) }
        }
        fq.reqs.addLast(Req(token, bytes))
        lastActiveMs[level] = nowMs
    }

    /** Remove a flow (connection closed). Returns the tokens of requests that never ran. */
    fun cancelFlow(flowId: Long): List<T> {
        val dropped = ArrayList<T>()
        for (t in 0 until levels) {
            val fq = index[t].remove(flowId) ?: continue
            order[t].remove(fq)
            fq.reqs.forEach { dropped.add(it.token) }
        }
        return dropped
    }

    fun hasWaiters(level: Int): Boolean = order[level].isNotEmpty()

    fun anyWaiters(): Boolean = order.any { it.isNotEmpty() }

    fun pendingBytes(level: Int): Long =
        order[level].sumOf { fq -> fq.reqs.sumOf { it.remaining.toLong() } }

    /** A level is active while it has waiting requests or moved bytes within the hold window. */
    fun isActive(level: Int, nowMs: Long): Boolean =
        order[level].isNotEmpty() || nowMs - lastActiveMs[level] < config.holdMs

    /** Run one scheduling tick and return everything that may proceed. */
    fun tick(nowMs: Long): List<Grant<T>> {
        val cfg = config
        val out = ArrayList<Grant<T>>()

        val dt = if (lastTickMs == Long.MIN_VALUE) cfg.tickMs
        else (nowMs - lastTickMs).coerceIn(0L, cfg.tickMs * MAX_CATCHUP_TICKS)
        lastTickMs = nowMs

        val refill = cfg.rateBytesPerSec * dt / 1000.0
        val maxBank = max(2.0 * cfg.rateBytesPerSec * cfg.tickMs / 1000.0, refill)
        var budget = min(carry + refill, maxBank)

        // Caps: level t is capped while any level above it is active.
        val capLeft = DoubleArray(levels) { Double.POSITIVE_INFINITY }
        var higherActive = false
        for (t in 0 until levels) {
            if (t > 0) {
                if (higherActive) {
                    val share = capShare(t, cfg)
                    val perTick = share * refill
                    val bank = max(2.0 * share * cfg.rateBytesPerSec * cfg.tickMs / 1000.0, perTick)
                    capCredit[t] = if (!capsWereOn[t]) perTick else min(capCredit[t] + perTick, bank)
                    capLeft[t] = capCredit[t]
                }
                capsWereOn[t] = higherActive
            }
            if (isActive(t, nowMs)) higherActive = true
        }

        // 1) Floors (guaranteed minimum share, only for levels that are waiting).
        for (t in 1 until levels) {
            if (order[t].isEmpty()) {
                floorCredit[t] = 0.0
                continue
            }
            floorCredit[t] = min(floorCredit[t] + floorShare(t, cfg) * refill, 2.0 * cfg.grantBytes)
            val allow = min(min(floorCredit[t], budget), capLeft[t]).toLong()
            if (allow > 0) {
                val served = serve(t, allow, nowMs, out)
                budget -= served
                floorCredit[t] -= served
                capLeft[t] -= served
            }
        }

        // 2) Strict priority for the rest: level 0, 1, 2, ...
        for (t in 0 until levels) {
            if (order[t].isEmpty()) continue
            val allow = min(budget, capLeft[t]).toLong()
            if (allow > 0) {
                val served = serve(t, allow, nowMs, out)
                budget -= served
                capLeft[t] -= served
            }
        }

        for (t in 1 until levels) if (capsWereOn[t]) capCredit[t] = max(0.0, capLeft[t])
        carry = min(max(budget, 0.0), maxBank)
        return out
    }

    /** Serve one level round-robin, at most [allowance] bytes in total. Returns bytes served. */
    private fun serve(t: Int, allowance: Long, nowMs: Long, out: MutableList<Grant<T>>): Long {
        var left = allowance
        val grantBytes = config.grantBytes.toLong()
        val queue = order[t]
        while (left > 0 && queue.isNotEmpty()) {
            val fq = queue.removeFirst()
            val req = fq.reqs.first()
            val turn = min(grantBytes, req.remaining.toLong())
            val n = min(turn, left).toInt()
            // A flow cut short by the allowance keeps its place at the front (same fairness fix as
            // PriorityPacerCore: a tiny floor grant must not burn its round-robin turn).
            val cutShort = n < turn
            req.remaining -= n
            left -= n
            val completed = req.remaining == 0
            if (completed) fq.reqs.removeFirst()
            out.add(Grant(t, fq.flowId, n, req.token, completed))
            if (fq.reqs.isEmpty()) index[t].remove(fq.flowId)
            else if (cutShort) queue.addFirst(fq)
            else queue.addLast(fq)
        }
        val served = allowance - left
        if (served > 0) lastActiveMs[t] = nowMs
        return served
    }

    private companion object {
        const val NEVER = -1_000_000_000_000L
        const val MAX_CATCHUP_TICKS = 10L
    }
}
