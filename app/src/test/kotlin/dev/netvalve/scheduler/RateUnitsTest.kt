package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateUnitsTest {
    @Test
    fun eightMbpsIsOneMegabytePerSecond() {
        assertEquals(1_000_000L, RateUnits.mbpsToBytesPerSec(8.0))
        assertEquals(8.0, RateUnits.bytesPerSecToMbps(1_000_000L), 1e-9)
    }

    @Test
    fun zeroOrNegativeMeansUnset() {
        assertEquals(0L, RateUnits.mbpsToBytesPerSec(0.0))
        assertEquals(0L, RateUnits.mbpsToBytesPerSec(-3.0))
        assertEquals(0L, RateUnits.mbpsToBytesPerSec(Double.NaN))
    }

    @Test
    fun parseAcceptsDotAndCommaAndTrims() {
        assertEquals(12.5, RateUnits.parseMbps(" 12.5 ")!!, 1e-9)
        assertEquals(12.5, RateUnits.parseMbps("12,5")!!, 1e-9)
        assertEquals(0.0, RateUnits.parseMbps("0")!!, 1e-9)
    }

    @Test
    fun parseRejectsBadInput() {
        assertNull(RateUnits.parseMbps(""))
        assertNull(RateUnits.parseMbps("abc"))
        assertNull(RateUnits.parseMbps("-1"))
        assertNull(RateUnits.parseMbps("20000"))
        assertNull(RateUnits.parseMbps("NaN"))
    }

    @Test
    fun formatTrimsZeros() {
        assertEquals("0", RateUnits.formatMbps(0L))
        assertEquals("8", RateUnits.formatMbps(1_000_000L))
        assertEquals("37.5", RateUnits.formatMbps(4_687_500L))
    }

    @Test
    fun mbpsTextUsesDecimalMegabits() {
        assertEquals("8.0 Mbps", RateUnits.mbpsText(1_000_000L))
        assertEquals("0.0 Mbps", RateUnits.mbpsText(0L))
        assertEquals("0.0 Mbps", RateUnits.mbpsText(-5L))
        assertEquals("21.0 Mbps", RateUnits.mbpsText(2_625_000L))
    }

    @Test
    fun averageMbpsIsTotalOverSeconds() {
        assertEquals(8.0, RateUnits.averageMbps(10_000_000L, 10), 1e-9)
        assertEquals(0.0, RateUnits.averageMbps(10_000_000L, 0), 1e-9)
        assertEquals(0.0, RateUnits.averageMbps(-1L, 5), 1e-9)
    }

    @Test
    fun settingsActiveOnlyForPacerModeWithRate() {
        val s = SchedulerSettings(SchedMode.PRIORITY_PACER, rateUpBytesPerSec = 0L, rateDownBytesPerSec = 500_000L)
        assertFalse(s.activeFor(Lane.UPLOAD))
        assertTrue(s.activeFor(Lane.DOWNLOAD))
        assertFalse(s.copy(mode = SchedMode.OFF).activeFor(Lane.DOWNLOAD))
        assertFalse(s.copy(mode = SchedMode.ADAPTIVE_THROTTLE).activeFor(Lane.DOWNLOAD))
    }
}
