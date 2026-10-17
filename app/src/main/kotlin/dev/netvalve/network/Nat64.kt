package dev.netvalve.network

import java.net.Inet4Address
import java.net.InetAddress

/**
 * NAT64 helpers (RFC 6052). On a network that has working IPv6 but no working IPv4 (seen on a friend's
 * phone hotspot, handoff problem 26), an IPv4 server can still be reached by dialling the IPv6 address
 * `64:ff9b::<IPv4>`, which a NAT64 gateway of the carrier translates. Only the well-known prefix
 * `64:ff9b::/96` is used; prefix discovery is not done.
 */
object Nat64 {
    /** The first 12 bytes of every synthesized address (64:ff9b::/96). */
    private val WELL_KNOWN_PREFIX: ByteArray = byteArrayOf(
        0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0,
    )

    /**
     * True only for a public IPv4 address. Private, loopback, link-local, multicast, "this network",
     * shared (100.64/10) and reserved (240/4) addresses are never translated: RFC 6052 does not allow the
     * well-known prefix for them and a gateway cannot reach them anyway.
     */
    fun isEligible(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        if (first == 0) return false
        if (first == 100 && second in 64..127) return false
        if (first >= 240) return false
        return true
    }

    /** `64:ff9b::a.b.c.d` for an IPv4 address. */
    fun synthesize(address: InetAddress): InetAddress {
        require(address is Inet4Address) { "NAT64 needs an IPv4 address" }
        val bytes = ByteArray(16)
        System.arraycopy(WELL_KNOWN_PREFIX, 0, bytes, 0, 12)
        System.arraycopy(address.address, 0, bytes, 12, 4)
        return InetAddress.getByAddress(bytes)
    }

    /**
     * True when the error says that the destination or the whole network cannot be reached
     * (EHOSTUNREACH or ENETUNREACH, as in the relay log "isConnected failed: EHOSTUNREACH (No route to host)").
     * A refused connection or a timeout is NOT an unreachable error: those do not trigger the NAT64 fallback.
     */
    fun isUnreachable(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            if (current is java.net.NoRouteToHostException) return true
            val text = current.message ?: ""
            if (text.contains("EHOSTUNREACH") || text.contains("ENETUNREACH") ||
                text.contains("No route to host") || text.contains("Network is unreachable")
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }
}

/**
 * Remembers for [holdMs] that plain IPv4 dials failed as unreachable and a NAT64 dial worked, so the next
 * flows go to NAT64 first instead of paying for a failed IPv4 attempt each time. After [holdMs] the next
 * IPv4 flow tries IPv4 again (the network may have changed). The clock is injected so it can be tested.
 */
class Ipv4PathMemory(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val holdMs: Long = 60_000L,
) {
    @Volatile private var armed: Boolean = false
    @Volatile private var untilMs: Long = 0L

    fun preferNat64(): Boolean = armed && nowMs() < untilMs

    /** Starts or extends the hold. Returns true when this call switched it on (it was not on before). */
    fun markIpv4Broken(): Boolean {
        val wasOn = preferNat64()
        untilMs = nowMs() + holdMs
        armed = true
        return !wasOn
    }
}
