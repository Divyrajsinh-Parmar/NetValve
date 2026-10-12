package dev.netvalve.scheduler

import kotlin.math.max
import kotlin.math.min

/**
 * Mode B core: a shared priority pacer, written as a deterministic state machine.
 *
 * Callers [submit] requests (a flow wants to move N bytes) and call [tick] once per
 * tick with the current time. [tick] returns the [Grant]s that may proceed now. The
 * class never sleeps and never reads a clock, so it runs under virtual time in unit
 * tests and in the simulator; a thin coroutine adapter (not part of this class)
 * turns grants into resumed `suspend` calls on the device.
 *
 * Per tick:
 *  1. budget = min(carry + R * dt, 2 * R * tick)            (banked budget is capped, no bursts)
 *  2. floors: Medium and Low first get floor * R * dt       (guaranteed share, no starvation)
 *  3. strict priority for the rest: High, then Medium, then Low
 *     (round-robin across flows inside a tier, at most [SchedulerConfig.grantBytes] per turn)
 *  4. while a High app is active (hold window) Medium is capped at alphaMedium * R and
 *     Low at alphaLow * R; when High is idle there are no caps (work-conserving)
 *  5. leftover budget is carried, but never beyond the cap in step 1
 *
 * Not thread-safe: the owner must serialise calls (one pump coroutine per direction).
 */
class PriorityPacerCore<T>(initial: SchedulerConfig) {

    /** Bytes that may proceed for one flow in this tick. [completed] means its request is fully served. */
    class Grant<T>(
        val tier: Tier,
        val flowId: Long,
        val bytes: Int,
        val token: T,
        val completed: Boolean,
    )

    private class Req<T>(val token: T, var remaining: Int)

    private class FlowQueue<T>(val flowId: Long) {
        val reqs = ArrayDeque<Req<T>>()
    }

    var config: SchedulerConfig = initial
        private set

    // Round-robin order of flows per tier, plus an index for O(1) lookup.
    private val order = Array(TIERS) { ArrayDeque<FlowQueue<T>>() }
    private val index = Array(TIERS) { HashMap<Long, FlowQueue<T>>() }

    private var lastTickMs = Long.MIN_VALUE
    private var carry = 0.0
    private val floorCredit = DoubleArray(TIERS)
    private val capCredit = DoubleArray(TIERS)
    private var lastHighMs = NEVER
    private var capsWereOn = false

    /** Change rate or parameters in place (calibration finished, user edited a value). */
    fun updateConfig(newConfig: SchedulerConfig) {
        config = newConfig
    }

    /** A flow asks to move [bytes] bytes. The same [flowId] must always use the same tier. */
    fun submit(tier: Tier, flowId: Long, bytes: Int, token: T, nowMs: Long) {
        require(bytes > 0) { "bytes must be > 0" }
        val t = tier.level
        val fq = index[t].getOrPut(flowId) {
            FlowQueue<T>(flowId).also { order[t].addLast(it) }
        }
        fq.reqs.addLast(Req(token, bytes))
        if (tier == Tier.HIGH) lastHighMs = nowMs
    }

    /** Remove a flow (connection closed). Returns the tokens of requests that never ran. */
    fun cancelFlow(flowId: Long): List<T> {
        val dropped = ArrayList<T>()
        for (t in 0 until TIERS) {
            val fq = index[t].remove(flowId) ?: continue
            order[t].remove(fq)
            fq.reqs.forEach { dropped.add(it.token) }
        }
        return dropped
    }

    fun hasWaiters(tier: Tier): Boolean = order[tier.level].isNotEmpty()

    fun pendingBytes(tier: Tier): Long =
        order[tier.level].sumOf { fq -> fq.reqs.sumOf { it.remaining.toLong() } }

    fun isHighActive(nowMs: Long): Boolean =
        hasWaiters(Tier.HIGH) || nowMs - lastHighMs < config.holdMs

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

        val capsOn = isHighActive(nowMs)
        val capLeft = DoubleArray(TIERS) { Double.POSITIVE_INFINITY }
        if (capsOn) {
            val share = doubleArrayOf(0.0, cfg.alphaMedium, cfg.alphaLow)
            for (t in MEDIUM..LOW) {
                val perTick = share[t] * refill
                val bank = max(2.0 * share[t] * cfg.rateBytesPerSec * cfg.tickMs / 1000.0, perTick)
                capCredit[t] = if (!capsWereOn) perTick else min(capCredit[t] + perTick, bank)
                capLeft[t] = capCredit[t]
            }
        }
        capsWereOn = capsOn

        // 1) Floors (guaranteed minimum share, only for tiers that are waiting).
        val floorShare = doubleArrayOf(0.0, cfg.floorMedium, cfg.floorLow)
        for (t in MEDIUM..LOW) {
            if (order[t].isEmpty()) {
                floorCredit[t] = 0.0
                continue
            }
            floorCredit[t] = min(floorCredit[t] + floorShare[t] * refill, 2.0 * cfg.grantBytes)
            val allow = min(min(floorCredit[t], budget), capLeft[t]).toLong()
            if (allow > 0) {
                val served = serve(t, allow, nowMs, out)
                budget -= served
                floorCredit[t] -= served
                capLeft[t] -= served
            }
        }

        // 2) Strict priority for the rest: High, Medium, Low.
        for (t in HIGH..LOW) {
            if (order[t].isEmpty()) continue
            val allow = min(budget, capLeft[t]).toLong()
            if (allow > 0) {
                val served = serve(t, allow, nowMs, out)
                budget -= served
                capLeft[t] -= served
            }
        }

        if (capsOn) for (t in MEDIUM..LOW) capCredit[t] = max(0.0, capLeft[t])
        carry = min(max(budget, 0.0), maxBank)
        return out
    }

    /** Serve one tier round-robin, at most [allowance] bytes in total. Returns bytes served. */
    private fun serve(t: Int, allowance: Long, nowMs: Long, out: MutableList<Grant<T>>): Long {
        var left = allowance
        val grantBytes = config.grantBytes.toLong()
        val queue = order[t]
        while (left > 0 && queue.isNotEmpty()) {
            val fq = queue.removeFirst()
            val req = fq.reqs.first()
            val turn = min(grantBytes, req.remaining.toLong())
            val n = min(turn, left).toInt()
            // A flow that was cut short by the allowance (not by its own grant or request size)
            // keeps its place at the front; otherwise a tiny floor grant would burn its turn and
            // the round-robin would become unfair (found by the unit tests).
            val cutShort = n < turn
            req.remaining -= n
            left -= n
            val completed = req.remaining == 0
            if (completed) fq.reqs.removeFirst()
            out.add(Grant(TIER_VALUES[t], fq.flowId, n, req.token, completed))
            if (fq.reqs.isEmpty()) index[t].remove(fq.flowId)
            else if (cutShort) queue.addFirst(fq)
            else queue.addLast(fq)
        }
        val served = allowance - left
        if (t == HIGH && served > 0) lastHighMs = nowMs
        return served
    }

    private companion object {
        const val TIERS = 3
        const val HIGH = 0
        const val MEDIUM = 1
        const val LOW = 2
        const val NEVER = -1_000_000_000_000L
        const val MAX_CATCHUP_TICKS = 10L
        val TIER_VALUES = Tier.values()
    }
}
