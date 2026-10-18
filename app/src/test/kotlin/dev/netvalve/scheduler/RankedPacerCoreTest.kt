package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the ranked pacer in virtual time. A "busy" flow always has a pending request. */
private class RankedHarness(config: SchedulerConfig, levels: Int = PriorityLevels.COUNT) {
    val core = RankedPacerCore<Long>(levels, config)
    val served = LongArray(levels)
    var now = 0L
    private val busy = HashMap<Long, Int>()
    private var nextToken = 0L

    fun keepBusy(level: Int, flowId: Long) {
        busy[flowId] = level
        core.submit(level, flowId, CHUNK, nextToken++, now)
    }

    /** One-off request (for example a small request of a chat app). */
    fun once(level: Int, flowId: Long, bytes: Int) {
        core.submit(level, flowId, bytes, nextToken++, now)
    }

    fun run(durationMs: Long, onTick: (Long) -> Unit = {}) {
        val tick = core.config.tickMs
        val end = now + durationMs
        while (now < end) {
            now += tick
            onTick(now)
            for (g in core.tick(now)) {
                served[g.level] += g.bytes.toLong()
                if (g.completed) busy[g.flowId]?.let { l -> core.submit(l, g.flowId, CHUNK, nextToken++, now) }
            }
        }
    }

    fun total() = served.sum()
    fun share(level: Int, rate: Long, ms: Long) = served[level].toDouble() / (rate * ms / 1000.0)

    companion object { const val CHUNK = 16 * 1024 * 1024 }
}

class RankedPacerCoreTest {

    private val rate = 625_000L // 5 Mbps in bytes per second
    private val cfg = SchedulerConfig(mode = SchedMode.PRIORITY_PACER, rateBytesPerSec = rate)
    private val r1 = PriorityLevels.FIRST_RANK
    private val r2 = PriorityLevels.FIRST_RANK + 1
    private val r3 = PriorityLevels.FIRST_RANK + 2
    private val normal = PriorityLevels.NORMAL

    @Test
    fun workConservingWhenOnlyNormalIsBusy() {
        val h = RankedHarness(cfg)
        h.keepBusy(normal, 1)
        h.run(10_000)
        val s = h.share(normal, rate, 10_000)
        assertTrue("normal should receive about all of R, got $s", s in 0.97..1.01)
    }

    @Test
    fun neverExceedsConfiguredRate() {
        val h = RankedHarness(cfg)
        h.keepBusy(r1, 1); h.keepBusy(r2, 2); h.keepBusy(r3, 3); h.keepBusy(normal, 4)
        h.run(20_000)
        val total = h.total().toDouble() / (rate * 20.0)
        assertTrue("total=$total", total in 0.97..1.01)
    }

    @Test
    fun order_secondRankBeatsThirdBeatsNormalWhenTheyAllWant() {
        // r2 takes what is left after the floors, r3 keeps its floor (0.10), normal its floor (0.05).
        val h = RankedHarness(cfg)
        h.keepBusy(r2, 2); h.keepBusy(r3, 3); h.keepBusy(normal, 4)
        h.run(30_000)
        val b = h.served[r2]; val c = h.served[r3]; val d = h.served[normal]
        assertTrue("b=$b c=$c d=$d", b > c && c > d)
        assertEquals(0.85, h.share(r2, rate, 30_000), 0.03)
        assertEquals(0.10, h.share(r3, rate, 30_000), 0.02)
        assertEquals(0.05, h.share(normal, rate, 30_000), 0.015)
    }

    @Test
    fun saturatingFirstRankLeavesOnlyTheFloorsToTheOthers() {
        // floors: two middle levels 0.10 each (r2 and r3 are the ones waiting here, the unused middle levels cost nothing), normal 0.05
        val h = RankedHarness(cfg)
        h.keepBusy(r1, 1); h.keepBusy(r2, 2); h.keepBusy(normal, 3)
        h.run(30_000)
        assertEquals(0.85, h.share(r1, rate, 30_000), 0.03)
        assertEquals(0.10, h.share(r2, rate, 30_000), 0.02)
        assertEquals(0.05, h.share(normal, rate, 30_000), 0.015)
    }

    @Test
    fun noStarvationForSixtySimulatedSeconds() {
        val h = RankedHarness(cfg)
        h.keepBusy(r1, 1); h.keepBusy(r2, 2); h.keepBusy(r3, 3); h.keepBusy(normal, 4)
        h.run(60_000)
        assertTrue(h.share(r2, rate, 60_000) >= 0.09)
        assertTrue(h.share(r3, rate, 60_000) >= 0.09)
        assertTrue(h.share(normal, rate, 60_000) >= 0.045)
    }

    @Test
    fun normalIsCappedWhileFirstRankIsActive() {
        // A light first-rank app (one small request per second) keeps the "active" state on all the time.
        val h = RankedHarness(cfg)
        h.keepBusy(normal, 9)
        h.once(r1, 1, 1_400) // active from the first tick on
        h.run(10_000) { now -> if (now % 1_000L == 0L) h.once(r1, 1, 1_400) }
        val s = h.share(normal, rate, 10_000)
        assertEquals("normal must be held near alphaLow (0.20)", 0.20, s, 0.03)
    }

    @Test
    fun normalGetsEverythingOnceAllMoreImportantLevelsAreIdle() {
        val h = RankedHarness(cfg)
        h.keepBusy(normal, 9)
        h.once(r1, 1, 1_400)
        h.run(2_500)      // the hold window (2 s) runs out
        val before = h.served[normal]
        h.run(10_000)
        val gained = (h.served[normal] - before).toDouble() / (rate * 10.0)
        assertTrue("after the hold window normal should use about all of R, got $gained", gained in 0.97..1.01)
    }

    @Test
    fun aMiddleRankIsCappedOnlyByMoreImportantRanksNotByLessImportantOnes() {
        // r2 busy, r1 idle for ever: r2 has no cap and takes (nearly) all of R even though normal waits.
        val h = RankedHarness(cfg)
        h.keepBusy(r2, 2); h.keepBusy(normal, 9)
        h.run(10_000)
        assertTrue("r2 got ${h.share(r2, rate, 10_000)}", h.share(r2, rate, 10_000) >= 0.89)

        // Now r1 becomes light-active: r2 is capped at alphaMedium (0.50) of R.
        val h2 = RankedHarness(cfg)
        h2.keepBusy(r2, 2)
        h2.once(r1, 1, 1_400) // active from the first tick on
        h2.run(10_000) { now -> if (now % 1_000L == 0L) h2.once(r1, 1, 1_400) }
        assertEquals(0.50, h2.share(r2, rate, 10_000), 0.04)
    }

    @Test
    fun grantsAreNeverLargerThanTheGrantSize() {
        val h = RankedHarness(cfg)
        h.keepBusy(r1, 1); h.keepBusy(normal, 2)
        var max = 0
        val tick = cfg.tickMs
        var now = 0L
        repeat(2_000) {
            now += tick
            for (g in h.core.tick(now)) if (g.bytes > max) max = g.bytes
        }
        assertTrue("max grant $max", max <= cfg.grantBytes)
    }

    @Test
    fun roundRobinInsideOneLevelIsFair() {
        val h = RankedHarness(cfg)
        val per = HashMap<Long, Long>()
        h.core.submit(normal, 1, RankedHarness.CHUNK, 1L, 0)
        h.core.submit(normal, 2, RankedHarness.CHUNK, 2L, 0)
        var now = 0L
        repeat(3_000) {
            now += cfg.tickMs
            for (g in h.core.tick(now)) per[g.flowId] = (per[g.flowId] ?: 0L) + g.bytes
        }
        val a = per[1L] ?: 0L; val b = per[2L] ?: 0L
        assertTrue("a=$a b=$b", a > 0 && b > 0 && Math.abs(a - b) < 0.1 * (a + b))
    }

    @Test
    fun cancelledFlowStopsConsumingBudget() {
        val h = RankedHarness(cfg)
        h.core.submit(normal, 1, 1_000_000, 1L, 0)
        val dropped = h.core.cancelFlow(1)
        assertEquals(1, dropped.size)
        assertTrue(!h.core.anyWaiters())
        assertTrue(h.core.tick(10).isEmpty())
    }

    @Test
    fun smallFirstRankRequestIsNotDelayedByAFullLowerLevel() {
        val h = RankedHarness(cfg)
        h.keepBusy(normal, 9)
        h.run(1_000)
        h.once(r1, 1, 1_400)
        var waitedMs = -1L
        val start = h.now
        while (waitedMs < 0 && h.now - start < 1_000) {
            h.now += cfg.tickMs
            for (g in h.core.tick(h.now)) if (g.flowId == 1L && g.completed) waitedMs = h.now - start
        }
        assertTrue("first rank waited $waitedMs ms", waitedMs in 0..40)
    }

    @Test(expected = IllegalArgumentException::class)
    fun levelOutsideTheRangeIsRejected() {
        RankedPacerCore<Long>(3, cfg).submit(3, 1, 10, 1L, 0)
    }

    @Test
    fun floorsThatWouldStarveLevelZeroAreRejected() {
        // 5 middle levels with floor 0.5 each cannot be allowed.
        var failed = false
        try {
            RankedPacerCore<Long>(7, SchedulerConfig(floorMedium = 0.4, floorLow = 0.05))
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
