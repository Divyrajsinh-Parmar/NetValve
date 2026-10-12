package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the pacer in virtual time. One "busy" flow always has a pending request. */
private class Harness(config: SchedulerConfig) {
    val core = PriorityPacerCore<Long>(config)
    val served = LongArray(3)          // bytes granted per tier level
    val servedByFlow = HashMap<Long, Long>()
    var maxGrant = 0
    var now = 0L
    private val busy = HashMap<Long, Tier>()
    private var nextToken = 0L

    fun keepBusy(tier: Tier, flowId: Long) {
        busy[flowId] = tier
        core.submit(tier, flowId, CHUNK, nextToken++, now)
    }

    /** One-off request (for example a small High request). */
    fun once(tier: Tier, flowId: Long, bytes: Int) {
        core.submit(tier, flowId, bytes, nextToken++, now)
    }

    fun run(durationMs: Long, onTick: (Long) -> Unit = {}) {
        val tick = core.config.tickMs
        val end = now + durationMs
        while (now < end) {
            now += tick
            onTick(now)
            for (g in core.tick(now)) {
                served[g.tier.level] += g.bytes.toLong()
                servedByFlow[g.flowId] = (servedByFlow[g.flowId] ?: 0L) + g.bytes
                if (g.bytes > maxGrant) maxGrant = g.bytes
                if (g.completed) busy[g.flowId]?.let { t -> core.submit(t, g.flowId, CHUNK, nextToken++, now) }
            }
        }
    }

    fun total() = served.sum()
    fun share(tier: Tier, rate: Long, ms: Long) = served[tier.level].toDouble() / (rate * ms / 1000.0)

    companion object { const val CHUNK = 16 * 1024 * 1024 }
}

class PriorityPacerCoreTest {

    private val rate = 625_000L // 5 Mbps in bytes per second
    private val cfg = SchedulerConfig(mode = SchedMode.PRIORITY_PACER, rateBytesPerSec = rate)

    @Test
    fun workConservingWhenOnlyLowIsBusy() {
        val h = Harness(cfg)
        h.keepBusy(Tier.LOW, 1)
        h.run(10_000)
        assertTrue("Low should receive about all of R, got ${h.share(Tier.LOW, rate, 10_000)}",
            h.share(Tier.LOW, rate, 10_000) in 0.97..1.01)
    }

    @Test
    fun neverExceedsConfiguredRate() {
        val h = Harness(cfg)
        h.keepBusy(Tier.HIGH, 1); h.keepBusy(Tier.MEDIUM, 2); h.keepBusy(Tier.LOW, 3)
        h.run(20_000)
        val total = h.total().toDouble() / (rate * 20.0)
        assertTrue("total=$total", total in 0.97..1.01)
    }

    @Test
    fun strictPriorityUnderFullSaturation() {
        // High saturates the link: High keeps ~85 percent, Medium and Low only get their floors.
        val h = Harness(cfg)
        h.keepBusy(Tier.HIGH, 1); h.keepBusy(Tier.MEDIUM, 2); h.keepBusy(Tier.LOW, 3)
        h.run(30_000)
        assertEquals(0.85, h.share(Tier.HIGH, rate, 30_000), 0.03)
        assertEquals(0.10, h.share(Tier.MEDIUM, rate, 30_000), 0.02)
        assertEquals(0.05, h.share(Tier.LOW, rate, 30_000), 0.015)
    }

    @Test
    fun noStarvationForSixtySimulatedSeconds() {
        val h = Harness(cfg)
        h.keepBusy(Tier.HIGH, 1); h.keepBusy(Tier.MEDIUM, 2); h.keepBusy(Tier.LOW, 3)
        h.run(60_000)
        assertTrue(h.share(Tier.MEDIUM, rate, 60_000) >= 0.09)
        assertTrue(h.share(Tier.LOW, rate, 60_000) >= 0.045)
    }

    @Test
    fun withoutFloorsLowAndMediumAreStarved() {
        // Ablation (configuration C5): proves what the floors buy.
        val h = Harness(cfg.copy(floorMedium = 0.0, floorLow = 0.0))
        h.keepBusy(Tier.HIGH, 1); h.keepBusy(Tier.MEDIUM, 2); h.keepBusy(Tier.LOW, 3)
        h.run(30_000)
        assertTrue(h.share(Tier.MEDIUM, rate, 30_000) < 0.01)
        assertTrue(h.share(Tier.LOW, rate, 30_000) < 0.01)
    }

    @Test
    fun capsApplyOnlyWhileHighIsActive() {
        // A light High app (one small request every 100 ms) keeps the caps switched on.
        val h = Harness(cfg)
        h.keepBusy(Tier.MEDIUM, 2); h.keepBusy(Tier.LOW, 3)
        var nextHigh = 0L
        h.run(20_000) { t -> if (t >= nextHigh) { h.once(Tier.HIGH, 1, 1_000); nextHigh = t + 100 } }
        assertEquals(0.50, h.share(Tier.MEDIUM, rate, 20_000), 0.03)
        assertEquals(0.20, h.share(Tier.LOW, rate, 20_000), 0.03)
    }

    @Test
    fun noCapsAfterHoldExpires() {
        val h = Harness(cfg)
        h.once(Tier.HIGH, 1, 1_000)
        h.run(100)                      // High finishes
        h.keepBusy(Tier.LOW, 3)
        h.run(cfg.holdMs + 200)         // hold window passes (Low is capped meanwhile)
        val before = h.served[Tier.LOW.level]
        h.run(10_000)
        val lowShare = (h.served[Tier.LOW.level] - before).toDouble() / (rate * 10.0)
        assertTrue("Low should be uncapped again, got $lowShare", lowShare > 0.97)
    }

    @Test
    fun grantsNeverExceedGrantBytes() {
        val h = Harness(cfg)
        h.keepBusy(Tier.HIGH, 1); h.keepBusy(Tier.LOW, 3)
        h.run(5_000)
        assertTrue(h.maxGrant <= cfg.grantBytes)
    }

    @Test
    fun roundRobinSharesEquallyInsideATier() {
        val h = Harness(cfg)
        h.keepBusy(Tier.LOW, 11); h.keepBusy(Tier.LOW, 12); h.keepBusy(Tier.LOW, 13)
        h.run(20_000)
        val a = h.servedByFlow.getValue(11).toDouble()
        val b = h.servedByFlow.getValue(12).toDouble()
        val c = h.servedByFlow.getValue(13).toDouble()
        val mean = (a + b + c) / 3
        assertTrue(a / mean in 0.95..1.05 && b / mean in 0.95..1.05 && c / mean in 0.95..1.05)
    }

    @Test
    fun bankedBudgetIsCappedAfterIdleAndAfterGaps() {
        val h = Harness(cfg)
        h.run(5_000)                    // idle for 5 s: nothing waiting
        h.keepBusy(Tier.LOW, 3)
        val grants = h.core.tick(h.now + cfg.tickMs)
        val bytes = grants.sumOf { it.bytes }
        assertTrue("burst $bytes", bytes <= 2.0 * rate * cfg.tickMs / 1000.0 + 1)

        // A long gap between ticks (the pump was paused) must not release a huge burst.
        val gapGrants = h.core.tick(h.now + 60_000)
        val gapBytes = gapGrants.sumOf { it.bytes }
        assertTrue("gap burst $gapBytes", gapBytes <= rate * 0.1 + 1)
    }

    @Test
    fun rateChangeAppliesInPlace() {
        val h = Harness(cfg)
        h.keepBusy(Tier.LOW, 3)
        h.run(5_000)
        val first = h.served[Tier.LOW.level]
        h.core.updateConfig(cfg.copy(rateBytesPerSec = rate / 2))
        h.run(5_000)
        val second = h.served[Tier.LOW.level] - first
        assertEquals(0.5, second.toDouble() / first, 0.05)
    }

    @Test
    fun cancelFlowDropsPendingRequests() {
        val core = PriorityPacerCore<String>(cfg)
        core.submit(Tier.LOW, 7, 10_000, "a", 0)
        core.submit(Tier.LOW, 7, 10_000, "b", 0)
        assertEquals(listOf("a", "b"), core.cancelFlow(7))
        assertTrue(!core.hasWaiters(Tier.LOW))
        assertEquals(0L, core.pendingBytes(Tier.LOW))
    }

    @Test
    fun highRequestCompletesQuicklyEvenWhileLowSaturates() {
        val h = Harness(cfg)
        h.keepBusy(Tier.LOW, 3); h.keepBusy(Tier.LOW, 4); h.keepBusy(Tier.LOW, 5)
        h.run(3_000)
        h.once(Tier.HIGH, 1, 1_400)
        val start = h.now
        var doneAt = -1L
        while (doneAt < 0 && h.now < start + 1_000) {
            h.now += cfg.tickMs
            for (g in h.core.tick(h.now)) {
                if (g.tier == Tier.HIGH && g.completed) doneAt = h.now
                if (g.completed && g.tier == Tier.LOW) h.core.submit(Tier.LOW, g.flowId, Harness.CHUNK, 0, h.now)
            }
        }
        assertTrue("High finished after ${doneAt - start} ms", doneAt >= 0 && doneAt - start <= 3 * cfg.tickMs)
    }
}
