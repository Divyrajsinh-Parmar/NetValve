package dev.netvalve.health

import dev.netvalve.scheduler.LevelTotals
import dev.netvalve.scheduler.PriorityLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HealthCountersTest {

    private fun sample() = HealthCounters(
        sinceMs = 1_700_000_000_000L,
        appStarts = 3,
        tunnelStarts = 5,
        runningMs = 7_200_000L,
        longestRunMs = 3_900_000L,
        establishFailures = 1,
        engineErrors = 2,
        autoSpeedFailures = 4,
        connectFailures = 9,
        lastFailureAtMs = 1_700_000_100_000L,
        downBytes = listOf(0, 100, 200, 0, 0, 0, 5_000),
        upBytes = listOf(0, 10, 20, 0, 0, 0, 500),
        waitMsSum = listOf(0, 50, 50, 0, 0, 0, 900),
        waitCount = listOf(0, 10, 10, 0, 0, 0, 90),
        waitMaxMs = listOf(0, 20, 30, 0, 0, 0, 400),
        unattributedBytes = 55,
    )

    @Test
    fun encodeThenDecodeGivesTheSameCounters() {
        val c = sample()
        assertEquals(c, HealthCounters.decode(c.encode()))
    }

    @Test
    fun damagedOrEmptyTextReadsAsEmpty() {
        assertEquals(HealthCounters(), HealthCounters.decode(null))
        assertEquals(HealthCounters(), HealthCounters.decode(""))
        assertEquals(HealthCounters(), HealthCounters.decode("\u0000\u0001 not a value"))
    }

    @Test
    fun unknownKeysAndBadNumbersAreSkipped() {
        val c = HealthCounters.decode("v1\nrunningMs=abc\ntunnelStarts=4\nfuture=1\ndown=1,x,3\n")
        assertEquals(0L, c.runningMs)
        assertEquals(4, c.tunnelStarts)
        assertEquals(listOf(1L, 0L, 3L, 0L, 0L, 0L, 0L), c.downBytes)
    }

    @Test
    fun negativeValuesAreNotAccepted() {
        assertEquals(0L, HealthCounters.decode("runningMs=-5").runningMs)
    }

    @Test
    fun plusLevelsAddsAndKeepsTheLargerMaximum() {
        val base = sample()
        val delta = LevelTotals.zero().copy(
            downBytes = List(PriorityLevels.COUNT) { 1L },
            waitMaxMs = List(PriorityLevels.COUNT) { if (it == 1) 99L else 0L },
            unattributedBytes = 5L,
        )
        val c = base.plusLevels(delta)
        assertEquals(101L, c.downBytes[1])
        assertEquals(99L, c.waitMaxMs[1])
        assertEquals(30L, c.waitMaxMs[2])
        assertEquals(60L, c.unattributedBytes)
    }

    @Test
    fun priorityAndNormalSums() {
        val c = sample()
        assertEquals(330L, c.priorityBytes())
        assertEquals(5_500L, c.normalBytes())
        assertEquals(5_830L, c.allBytes())
        assertEquals(5.0, c.priorityAvgWaitMs()!!, 0.0001)
        assertEquals(10.0, c.normalAvgWaitMs()!!, 0.0001)
        assertEquals(20L, c.priorityWaitChunks())
        assertEquals(30L, c.priorityMaxWaitMs())
        assertEquals(400L, c.normalMaxWaitMs())
        assertEquals(7, c.totalFailures()) // 1 + 2 + 4
    }

    @Test
    fun noWaitDataGivesNull() {
        assertNull(HealthCounters().priorityAvgWaitMs())
        assertNull(HealthCounters().attributedPercent())
    }

    @Test
    fun attributedPercentIsComputedFromUnattributedBytes() {
        val c = HealthCounters(downBytes = listOf(0, 0, 0, 0, 0, 0, 1_000), unattributedBytes = 50)
        assertEquals(95.0, c.attributedPercent()!!, 0.0001)
        assertNotNull(c.attributedPercent())
    }
}
