package dev.netvalve.scheduler.sim

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Checks the roadmap success criteria at algorithm level (no phone needed):
 *  - latency: Mode B p95 is at most 50 percent of the unscheduled p95
 *  - work conservation: with High idle, Mode B Low goodput is at least 85 percent of R
 *  - no starvation: with floors, Low still moves bytes while High saturates the link
 * It also pins down the honest weaknesses: Mode A's tail latency, and the cost of the hold time.
 */
class SchedulerSimulationTest {

    companion object {
        private lateinit var all: Map<String, Map<SimMode, SimResult>>

        @BeforeClass @JvmStatic
        fun runOnce() {
            val results = SimulationMain.runAllScenarios()
            println(SimulationMain.table(results))
            all = results.associate { (name, rs) -> name.substringBefore(' ') to rs.associateBy { it.mode } }
        }
    }

    private fun r(sc: String, m: SimMode) = all.getValue(sc).getValue(m)

    @Test
    fun modeBHalvesTheTailLatencyOrBetter() {
        val fifo = r("S1", SimMode.FIFO)
        val b = r("S1", SimMode.MODE_B)
        assertTrue("fifo p95=${fifo.p95} modeB p95=${b.p95}", b.p95 <= 0.5 * fifo.p95)
        assertTrue("modeB should stay close to an idle link, p99=${b.p99}", b.p99 <= 60)
    }

    @Test
    fun fifoShowsBufferbloat() {
        // 256 KB at 625 kB/s is about 410 ms of queue, plus base RTT.
        assertTrue(r("S1", SimMode.FIFO).p95 in 380..520)
    }

    @Test
    fun modeAHasTheDocumentedFirstPacketWeakness() {
        val a = r("S1", SimMode.MODE_A)
        val b = r("S1", SimMode.MODE_B)
        assertTrue("typical latency is fine: p50=${a.p50}", a.p50 <= 60)
        assertTrue("but the first request after idle meets a full queue: max=${a.max}", a.max >= 300)
        assertTrue("Mode B has no such spike: max=${b.max}", b.max < a.max / 4)
    }

    @Test
    fun modeBIsWorkConservingWhileStaticCapIsNot() {
        val b = r("S4", SimMode.MODE_B)
        val cap = r("S4", SimMode.STATIC_CAP)
        val rateR = b.params.rateR.toDouble()
        assertTrue("modeB low=${b.lowGoodput}", b.lowGoodput >= 0.85 * rateR)
        assertTrue("static cap wastes the link: ${cap.lowGoodput}", cap.lowGoodput <= 0.25 * rateR)
        assertTrue(b.lowGoodput > 3 * cap.lowGoodput)
    }

    @Test
    fun hysteresisCostsSomeIdleThroughputButStaysAboveEightyPercentOfR() {
        val b = r("S1", SimMode.MODE_B)
        val rateR = b.params.rateR.toDouble()
        assertTrue("idle-window Low goodput=${b.lowGoodputWhileHighIdle}", b.lowGoodputWhileHighIdle >= 0.80 * rateR)
    }

    @Test
    fun floorsPreventStarvationAndAblationProvesIt() {
        val withFloors = r("S5", SimMode.MODE_B)
        val without = r("S5", SimMode.MODE_B_NO_FLOORS)
        val rateR = withFloors.params.rateR.toDouble()
        assertTrue("with floors Low=${withFloors.lowGoodput}", withFloors.lowGoodput >= 0.04 * rateR)
        assertTrue("without floors Low=${without.lowGoodput}", without.lowGoodput <= 0.01 * rateR)
    }

    @Test
    fun priorityCostsAboutTenPercentPeakThroughput() {
        // R is 0.9 x link, so total goodput under Mode B is about 90 percent of the unscheduled link.
        val b = r("S4", SimMode.MODE_B)
        val fifo = r("S4", SimMode.FIFO)
        val ratio = b.lowGoodput / fifo.lowGoodput
        assertTrue("ratio=$ratio", ratio in 0.85..0.95)
    }
}
