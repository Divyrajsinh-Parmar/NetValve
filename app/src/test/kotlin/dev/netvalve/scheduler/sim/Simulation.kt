package dev.netvalve.scheduler.sim

import dev.netvalve.scheduler.AdaptiveThrottle
import dev.netvalve.scheduler.PriorityPacerCore
import dev.netvalve.scheduler.SchedMode
import dev.netvalve.scheduler.SchedulerConfig
import dev.netvalve.scheduler.Tier

/**
 * Algorithm-level model of ONE bottleneck link shared by a High app and a Low app.
 *
 * It answers a single question without any phone: if the device controls its own
 * traffic like mode X, how long does a small High request wait? Time is simulated in
 * 1 ms steps, so a 60 s run takes milliseconds and is repeatable.
 *
 * Model (kept deliberately simple, every simplification is listed):
 *  - The bottleneck (router, access point or cell scheduler) is a FIFO of [SimParams.bufferBytes]
 *    served at [SimParams.linkBytesPerSec]. A full buffer tail-drops Low traffic.
 *  - The measured High requests are never dropped (this UNDER-states FIFO latency: real drops
 *    would add retransmission delay).
 *  - Low is a greedy bulk transfer: it always has data. Without scheduling it offers
 *    [SimParams.lowOfferFactor] x link rate, so the bottleneck queue stays full (bufferbloat).
 *    TCP back-off is not modelled; the standing queue is what matters here.
 *  - Reported latency = wait in the device scheduler + wait in the bottleneck queue + transmission
 *    + a fixed [SimParams.baseRttMs].
 */
enum class SimMode(val id: String, val label: String) {
    FIFO("C1", "VPN on, scheduling OFF"),
    STATIC_CAP("C2", "Static cap on Low (R/5)"),
    MODE_A("C3", "Mode A adaptive throttle"),
    MODE_B("C4", "Mode B priority pacer"),
    MODE_B_NO_FLOORS("C5", "Mode B without floors"),
}

data class SimParams(
    val linkBytesPerSec: Long = 625_000,        // 5 Mbps bottleneck
    val bufferBytes: Long = 256 * 1024,         // bottleneck FIFO (about 410 ms at 5 Mbps)
    val baseRttMs: Long = 20,
    val durationMs: Long = 60_000,
    val highRequestBytes: Int = 1_400,
    val highEveryMs: Long = 200,
    val highOnMs: Long = 10_000,                // High app active for this long ...
    val highOffMs: Long = 10_000,               // ... then silent for this long. highOnMs = 0 means never.
    val highSaturating: Boolean = false,        // High is itself a bulk flow (starvation test)
    val lowFlows: Int = 3,
    val lowChunkBytes: Int = 16 * 1024,
    val highBulkChunkBytes: Int = 1024 * 1024,  // large write, so the model does not leak budget between writes
    val lowOfferFactor: Double = 2.0,
    val pacerFraction: Double = 0.9,            // R = 0.9 x link, as calibration would set it
    val staticCapDivisor: Int = 5,
) {
    val rateR: Long get() = (linkBytesPerSec * pacerFraction).toLong()
}

class SimResult(
    val mode: SimMode,
    val params: SimParams,
    val highLatenciesMs: List<Long>,
    val lowDeliveredBytes: Long,
    val lowDeliveredWhileHighIdleBytes: Long,
    val highIdleMs: Long,
    val highBulkDeliveredBytes: Long,
) {
    fun percentile(p: Double): Long {
        if (highLatenciesMs.isEmpty()) return 0
        val s = highLatenciesMs.sorted()
        val idx = Math.ceil(p / 100.0 * s.size).toInt().coerceIn(1, s.size) - 1
        return s[idx]
    }

    val p50 get() = percentile(50.0)
    val p95 get() = percentile(95.0)
    val p99 get() = percentile(99.0)
    val max get() = highLatenciesMs.maxOrNull() ?: 0L

    /** Low goodput over the whole run, bytes per second. */
    val lowGoodput get() = lowDeliveredBytes * 1000.0 / params.durationMs

    /** Low goodput only while the High app was silent (work-conservation check). */
    val lowGoodputWhileHighIdle get() =
        if (highIdleMs == 0L) 0.0 else lowDeliveredWhileHighIdleBytes * 1000.0 / highIdleMs
}

private class Chunk(var bytes: Double, val measuredCreatedMs: Long, val kind: Kind) {
    enum class Kind { HIGH_REQUEST, HIGH_BULK, LOW }
}

/** What a pacer request carries: which kind of traffic it is and when it was created. */
private class Tok(val kind: Chunk.Kind, val createdMs: Long)

object Simulation {

    fun run(mode: SimMode, p: SimParams): SimResult {
        val queue = ArrayDeque<Chunk>()
        var queueBytes = 0.0
        val latencies = ArrayList<Long>()
        var lowDelivered = 0.0
        var lowDeliveredIdle = 0.0
        var highBulkDelivered = 0.0
        var highIdleMs = 0L

        val link = p.linkBytesPerSec / 1000.0
        val rate = p.rateR

        // Device-side scheduler state
        val flowsConfig = SchedulerConfig(
            mode = SchedMode.PRIORITY_PACER,
            rateBytesPerSec = rate,
            floorMedium = if (mode == SimMode.MODE_B_NO_FLOORS) 0.0 else 0.10,
            floorLow = if (mode == SimMode.MODE_B_NO_FLOORS) 0.0 else 0.05,
        )
        val pacer = PriorityPacerCore<Tok>(flowsConfig)
        val adaptive = AdaptiveThrottle(flowsConfig)
        val pacerMode = mode == SimMode.MODE_B || mode == SimMode.MODE_B_NO_FLOORS
        val lowFlowIds = (1..p.lowFlows).map { it.toLong() }
        val highBulkFlowId = 100L

        if (pacerMode) {
            lowFlowIds.forEach { pacer.submit(Tier.LOW, it, p.lowChunkBytes, Tok(Chunk.Kind.LOW, 0), 0) }
            if (p.highSaturating) pacer.submit(Tier.HIGH, highBulkFlowId, p.highBulkChunkBytes, Tok(Chunk.Kind.HIGH_BULK, 0), 0)
        }

        var lowFrac = 0.0
        var highBulkFrac = 0.0

        fun inject(c: Chunk) {
            if (c.kind != Chunk.Kind.HIGH_REQUEST && queueBytes + c.bytes > p.bufferBytes) return // tail drop
            queue.addLast(c)
            queueBytes += c.bytes
        }

        for (t in 0L until p.durationMs) {
            val cycle = p.highOnMs + p.highOffMs
            val highPhase = p.highOnMs > 0 && (t % cycle) < p.highOnMs
            val highActive = highPhase || (p.highSaturating)
            if (!highActive) highIdleMs++

            // ---- 1) Traffic wants to leave the device ---------------------------------
            val newHighRequest = !p.highSaturating && highPhase && t % p.highEveryMs == 0L
            if (pacerMode) {
                if (newHighRequest) pacer.submit(Tier.HIGH, 0, p.highRequestBytes, Tok(Chunk.Kind.HIGH_REQUEST, t), t)
                if (t % flowsConfig.tickMs == 0L) {
                    for (g in pacer.tick(t)) {
                        val kind = g.token.kind
                        val created = if (kind == Chunk.Kind.HIGH_REQUEST && g.completed) g.token.createdMs else -1L
                        inject(Chunk(g.bytes.toDouble(), created, kind))
                        if (g.completed && kind != Chunk.Kind.HIGH_REQUEST) {
                            val tier = if (kind == Chunk.Kind.LOW) Tier.LOW else Tier.HIGH
                            val size = if (kind == Chunk.Kind.LOW) p.lowChunkBytes else p.highBulkChunkBytes
                            pacer.submit(tier, g.flowId, size, Tok(kind, t), t)
                        }
                    }
                }
            } else {
                if (newHighRequest) {
                    adaptive.onHighBytes(t)
                    inject(Chunk(p.highRequestBytes.toDouble(), t, Chunk.Kind.HIGH_REQUEST))
                }
                if (p.highSaturating) {
                    adaptive.onHighBytes(t)
                    highBulkFrac += p.lowOfferFactor * link
                    val n = highBulkFrac.toLong()
                    if (n > 0) { highBulkFrac -= n; inject(Chunk(n.toDouble(), -1, Chunk.Kind.HIGH_BULK)) }
                }
                val lowRate = when (mode) {
                    SimMode.FIFO -> p.lowOfferFactor * link
                    SimMode.STATIC_CAP -> rate.toDouble() / p.staticCapDivisor / 1000.0
                    else -> adaptive.capBytesPerSec(Tier.LOW, t)?.let { it / 1000.0 } ?: (p.lowOfferFactor * link)
                }
                lowFrac += lowRate
                val n = lowFrac.toLong()
                if (n > 0) { lowFrac -= n; inject(Chunk(n.toDouble(), -1, Chunk.Kind.LOW)) }
            }

            // ---- 2) The bottleneck serves 1 ms worth of bytes -------------------------
            var cap = link
            while (cap > 1e-9 && queue.isNotEmpty()) {
                val head = queue.first()
                val take = minOf(head.bytes, cap)
                head.bytes -= take
                cap -= take
                queueBytes -= take
                when (head.kind) {
                    Chunk.Kind.LOW -> { lowDelivered += take; if (!highActive) lowDeliveredIdle += take }
                    Chunk.Kind.HIGH_BULK -> highBulkDelivered += take
                    Chunk.Kind.HIGH_REQUEST -> Unit
                }
                if (head.bytes <= 1e-9) {
                    queue.removeFirst()
                    if (head.kind == Chunk.Kind.HIGH_REQUEST && head.measuredCreatedMs >= 0) {
                        latencies.add(t + 1 - head.measuredCreatedMs + p.baseRttMs)
                    }
                }
            }
        }

        return SimResult(mode, p, latencies, lowDelivered.toLong(), lowDeliveredIdle.toLong(), highIdleMs, highBulkDelivered.toLong())
    }

    fun runAll(p: SimParams, modes: List<SimMode> = SimMode.values().toList()): List<SimResult> =
        modes.map { run(it, p) }
}
