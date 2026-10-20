package dev.netvalve.calibration

import dev.netvalve.data.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatePolicyTest {

    @Test
    fun aUsableMeasurementWins() {
        assertEquals(3_000_000L, RatePolicy.choose(measured = 3_000_000L, known = 5_000_000L))
    }

    @Test
    fun aFailedMeasurementKeepsTheLastKnownValue() {
        assertEquals(5_000_000L, RatePolicy.choose(measured = null, known = 5_000_000L))
    }

    @Test
    fun aSillyTinyMeasurementIsTreatedAsFailed() {
        assertEquals(5_000_000L, RatePolicy.choose(measured = 10L, known = 5_000_000L))
        assertFalse(RatePolicy.isUsable(10L))
        assertFalse(RatePolicy.isUsable(null))
        assertTrue(RatePolicy.isUsable(RatePolicy.MIN_USABLE_BYTES_PER_SEC))
    }

    @Test
    fun noMeasurementAndNothingKnownMeansNoShaping() {
        assertEquals(0L, RatePolicy.choose(measured = null, known = null))
        assertEquals(0L, RatePolicy.choose(measured = 5L, known = 7L))
    }

    @Test
    fun quietMeansLessThanTheLimit() {
        assertTrue(RatePolicy.isQuiet(0))
        assertTrue(RatePolicy.isQuiet(RatePolicy.QUIET_DOWNLOAD_BYTES_PER_SEC - 1))
        assertFalse(RatePolicy.isQuiet(RatePolicy.QUIET_DOWNLOAD_BYTES_PER_SEC))
    }

    @Test
    fun onlyWifiMobileAndEthernetAreMeasured() {
        assertEquals("WIFI", RatePolicy.networkKey(NetworkType.WIFI))
        assertEquals("MOBILE", RatePolicy.networkKey(NetworkType.MOBILE))
        assertEquals("ETHERNET", RatePolicy.networkKey(NetworkType.ETHERNET))
        assertNull(RatePolicy.networkKey(NetworkType.VPN))
        assertNull(RatePolicy.networkKey(NetworkType.OTHER))
        assertNull(RatePolicy.networkKey(NetworkType.NONE))
    }

    @Test
    fun testParametersMatchTheStudentsSpecification() {
        // About 8 s download, first 2 s discarded, R = 0.9 x steady speed (CalibratorMath.SAFETY_FACTOR).
        assertEquals(8_000L, RatePolicy.MEASURE_MS)
        assertEquals(2_000L, RatePolicy.DISCARD_MS)
        assertEquals("http://speedtest.mumbai1.linode.com/100MB-mumbai.bin", RatePolicy.DOWNLOAD_URL)
    }
}
