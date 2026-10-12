package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerSupportTest {

    @Test
    fun unknownLevelsAreMedium() {
        assertEquals(Tier.MEDIUM, Tier.fromLevel(99))
        assertEquals(Tier.HIGH, Tier.fromLevel(0))
        assertEquals(Tier.LOW, Tier.fromLevel(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsFloorsThatLeaveNoRoomForHigh() {
        SchedulerConfig(floorMedium = 0.5, floorLow = 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsZeroRate() {
        SchedulerConfig(rateBytesPerSec = 0)
    }

    @Test
    fun adaptiveThrottleCapsOnlyWhileHighIsActive() {
        val cfg = SchedulerConfig(rateBytesPerSec = 1_000_000)
        val a = AdaptiveThrottle(cfg)
        assertNull(a.capBytesPerSec(Tier.LOW, 0))
        a.onHighBytes(1_000)
        assertEquals(200_000L, a.capBytesPerSec(Tier.LOW, 1_500))
        assertEquals(500_000L, a.capBytesPerSec(Tier.MEDIUM, 1_500))
        assertNull(a.capBytesPerSec(Tier.HIGH, 1_500))
        assertTrue(a.isHighActive(2_999))
        assertFalse(a.isHighActive(3_000))
        assertNull(a.capBytesPerSec(Tier.LOW, 3_000))
    }

    @Test
    fun calibratorDiscardsSlowStartAndAppliesSafetyFactor() {
        // 1 MB/s steady, but the first 2 s ramp up slowly.
        val samples = ArrayList<CalibratorMath.Sample>()
        var bytes = 0L
        for (t in 0..8_000 step 500) {
            bytes += if (t < 2_000) 100_000 else 500_000
            samples.add(CalibratorMath.Sample(t.toLong(), bytes))
        }
        val steady = CalibratorMath.steadyRate(samples)!!
        assertEquals(1_000_000.0, steady.toDouble(), 20_000.0)
        assertEquals(900_000.0, CalibratorMath.pacerRate(samples)!!.toDouble(), 20_000.0)
    }

    @Test
    fun calibratorReturnsNullOnUnusableData() {
        assertNull(CalibratorMath.steadyRate(emptyList()))
        assertNull(CalibratorMath.steadyRate(listOf(CalibratorMath.Sample(0, 0), CalibratorMath.Sample(100, 10))))
        val flat = (0..8).map { CalibratorMath.Sample(it * 1000L, 5_000) }
        assertNull(CalibratorMath.steadyRate(flat))
    }
}
