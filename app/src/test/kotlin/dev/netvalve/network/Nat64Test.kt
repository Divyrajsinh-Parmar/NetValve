package dev.netvalve.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

class Nat64Test {

    private fun ip(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    @Test
    fun synthesizeGivesTheWellKnownPrefixForTheTele2Address() {
        val result = Nat64.synthesize(ip(90, 130, 70, 73))
        assertTrue(result is Inet6Address)
        val expected = byteArrayOf(
            0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0,
            0x5A, 0x82.toByte(), 0x46, 0x49,
        )
        assertArrayEquals(expected, result.address)
    }

    @Test
    fun publicIpv4IsEligible() {
        assertTrue(Nat64.isEligible(ip(90, 130, 70, 73)))
        assertTrue(Nat64.isEligible(ip(1, 1, 1, 1)))
        assertTrue(Nat64.isEligible(ip(8, 8, 8, 8)))
    }

    @Test
    fun privateAndSpecialIpv4AreNotEligible() {
        assertFalse(Nat64.isEligible(ip(10, 111, 0, 2)))
        assertFalse(Nat64.isEligible(ip(172, 25, 67, 150)))
        assertFalse(Nat64.isEligible(ip(192, 168, 1, 1)))
        assertFalse(Nat64.isEligible(ip(127, 0, 0, 1)))
        assertFalse(Nat64.isEligible(ip(169, 254, 1, 1)))
        assertFalse(Nat64.isEligible(ip(224, 0, 0, 1)))
        assertFalse(Nat64.isEligible(ip(0, 0, 0, 0)))
        assertFalse(Nat64.isEligible(ip(100, 64, 0, 1)))
        assertFalse(Nat64.isEligible(ip(255, 255, 255, 255)))
    }

    @Test
    fun ipv6IsNotEligible() {
        val v6 = InetAddress.getByAddress(ByteArray(16) { if (it == 0) 0x20 else if (it == 15) 1 else 0 })
        assertFalse(Nat64.isEligible(v6))
    }

    @Test
    fun theRealRelayErrorTextCountsAsUnreachable() {
        val e = ConnectException(
            "failed to connect to /1.1.1.1 (port 853) from /172.25.67.150 (port 43595) after 10000ms: " +
                "isConnected failed: EHOSTUNREACH (No route to host)",
        )
        assertTrue(Nat64.isUnreachable(e))
    }

    @Test
    fun networkUnreachableAndNestedCausesCountAsUnreachable() {
        assertTrue(Nat64.isUnreachable(NoRouteToHostException()))
        val inner = ConnectException("connect failed: ENETUNREACH (Network is unreachable)")
        assertTrue(Nat64.isUnreachable(RuntimeException("wrapper", inner)))
    }

    @Test
    fun refusedAndTimeoutAreNotUnreachable() {
        assertFalse(Nat64.isUnreachable(ConnectException("failed to connect: ECONNREFUSED (Connection refused)")))
        assertFalse(Nat64.isUnreachable(SocketTimeoutException("connect timed out")))
        assertFalse(Nat64.isUnreachable(RuntimeException()))
    }

    @Test
    fun memoryHoldsForTheConfiguredTimeAndThenForgets() {
        var now = 1_000L
        val memory = Ipv4PathMemory(nowMs = { now }, holdMs = 1_000L)
        assertFalse(memory.preferNat64())
        assertTrue(memory.markIpv4Broken())
        assertTrue(memory.preferNat64())
        now = 1_999L
        assertTrue(memory.preferNat64())
        now = 2_000L
        assertFalse(memory.preferNat64())
        assertTrue(memory.markIpv4Broken())
    }

    @Test
    fun markingAgainWhileOnExtendsTheHoldAndReportsNotNew() {
        var now = 0L
        val memory = Ipv4PathMemory(nowMs = { now }, holdMs = 1_000L)
        assertTrue(memory.markIpv4Broken())
        now = 900L
        assertFalse(memory.markIpv4Broken())
        now = 1_500L
        assertTrue(memory.preferNat64())
        now = 1_900L
        assertFalse(memory.preferNat64())
    }
}
