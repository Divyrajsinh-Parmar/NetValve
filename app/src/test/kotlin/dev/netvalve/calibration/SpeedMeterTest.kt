package dev.netvalve.calibration

import dev.netvalve.scheduler.CalibratorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {

    /** A fake link of [bytesPerSec]: each read of [chunk] bytes takes chunk / rate seconds of fake time. */
    private class FakeLink(val bytesPerSec: Long, val chunk: Int, val endAtBytes: Long = Long.MAX_VALUE) {
        var nowMs = 1_000L
        var sent = 0L
        fun clock(): Long = nowMs
        fun read(): Int {
            if (sent >= endAtBytes) return -1
            nowMs += chunk * 1000L / bytesPerSec
            sent += chunk
            return chunk
        }
    }

    @Test
    fun steadyLinkGivesZeroPointNineTimesItsSpeed() {
        val link = FakeLink(bytesPerSec = 5_000_000L, chunk = 50_000)
        val samples = SpeedMeter.sample(8_000, 250, link::clock, link::read)
        val rate = CalibratorMath.pacerRate(samples, discardMs = 2_000)
        assertNotNull(rate)
        assertEquals(4_500_000.0, rate!!.toDouble(), 100_000.0)
    }

    @Test
    fun theFirstTwoSecondsOfSlowStartAreThrownAway() {
        // 1 MB/s for the first 2 s, then 4 MB/s: the steady rate must be about 4 MB/s (times 0.9),
        // not the average over the whole test. The fake clock runs in fractions of a millisecond.
        var timeMs = 0.0
        val samples = SpeedMeter.sample(
            durationMs = 8_000, everyMs = 250,
            nowMs = { timeMs.toLong() },
            read = {
                val rate = if (timeMs < 2_000.0) 1_000_000.0 else 4_000_000.0
                timeMs += 10_000 * 1000.0 / rate
                10_000
            },
        )
        val rate = CalibratorMath.pacerRate(samples, discardMs = 2_000)!!
        assertEquals(3_600_000.0, rate.toDouble(), 150_000.0)
    }

    @Test
    fun stopsAfterTheDuration() {
        val link = FakeLink(bytesPerSec = 1_000_000L, chunk = 10_000)
        val samples = SpeedMeter.sample(3_000, 250, link::clock, link::read)
        val last = samples.last().tMs
        assertTrue("last sample at $last ms", last in 3_000..3_100)
    }

    @Test
    fun endOfStreamEndsTheTestAndTooShortIsUnusable() {
        // The file ends after 0.5 s: not enough data after the 2 s discard, so no rate (null).
        val link = FakeLink(bytesPerSec = 1_000_000L, chunk = 10_000, endAtBytes = 500_000L)
        val samples = SpeedMeter.sample(8_000, 250, link::clock, link::read)
        assertNull(CalibratorMath.pacerRate(samples, discardMs = 2_000))
    }

    @Test
    fun keepGoingFalseStopsAtOnce() {
        val link = FakeLink(bytesPerSec = 1_000_000L, chunk = 10_000)
        var reads = 0
        val samples = SpeedMeter.sample(8_000, 250, link::clock, { reads++; link.read() }, keepGoing = { reads < 5 })
        assertEquals(5, reads)
        assertTrue(samples.isNotEmpty())
    }
}
