package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelByteCounterTest {

    @Test
    fun countsBytesPerLevelAndLane() {
        val c = LevelByteCounter()
        c.addBytes(1, Lane.DOWNLOAD, 1000, attributed = true)
        c.addBytes(1, Lane.UPLOAD, 200, attributed = true)
        c.addBytes(PriorityLevels.NORMAL, Lane.DOWNLOAD, 5000, attributed = true)
        val t = c.snapshot()
        assertEquals(1000L, t.downBytes[1])
        assertEquals(200L, t.upBytes[1])
        assertEquals(5000L, t.downBytes[PriorityLevels.NORMAL])
        assertEquals(0L, t.unattributedBytes)
    }

    @Test
    fun outOfRangeLevelsAreClampedToNormalAndNeverThrow() {
        val c = LevelByteCounter()
        c.addBytes(-5, Lane.DOWNLOAD, 10, attributed = false)
        c.addBytes(99, Lane.UPLOAD, 20, attributed = false)
        c.addBytes(2, Lane.UPLOAD, -7, attributed = true) // ignored
        c.addBytes(2, Lane.UPLOAD, 0, attributed = true) // ignored
        c.addWait(99, 5)
        val t = c.snapshot()
        assertEquals(10L, t.downBytes[PriorityLevels.NORMAL])
        assertEquals(20L, t.upBytes[PriorityLevels.NORMAL])
        assertEquals(0L, t.upBytes[2])
        assertEquals(30L, t.unattributedBytes)
        assertEquals(1L, t.waitCount[PriorityLevels.NORMAL])
    }

    @Test
    fun waitSumCountAndMaximum() {
        val c = LevelByteCounter()
        c.addWait(1, 10)
        c.addWait(1, 30)
        c.addWait(1, 20)
        c.addWait(1, -4) // counted as 0
        val t = c.snapshot()
        assertEquals(60L, t.waitMsSum[1])
        assertEquals(4L, t.waitCount[1])
        assertEquals(30L, t.waitMaxMs[1])
    }

    @Test
    fun deltaSubtractsSumsAndKeepsTheCurrentMaximum() {
        val c = LevelByteCounter()
        c.addBytes(1, Lane.DOWNLOAD, 100, true)
        c.addWait(1, 40)
        val first = c.snapshot()
        c.addBytes(1, Lane.DOWNLOAD, 50, true)
        c.addWait(1, 10)
        val delta = c.snapshot().deltaFrom(first)
        assertEquals(50L, delta.downBytes[1])
        assertEquals(10L, delta.waitMsSum[1])
        assertEquals(1L, delta.waitCount[1])
        assertEquals(40L, delta.waitMaxMs[1])
    }

    @Test
    fun deltaNeverGoesNegative() {
        val big = LevelTotals.zero().copy(downBytes = List(PriorityLevels.COUNT) { 100L })
        val small = LevelTotals.zero()
        assertEquals(0L, small.deltaFrom(big).downBytes[0])
    }
}
