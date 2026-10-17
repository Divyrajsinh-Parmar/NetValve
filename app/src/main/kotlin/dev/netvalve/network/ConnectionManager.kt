package dev.netvalve.network

import dev.netvalve.log.LogCategory
import dev.netvalve.log.Logger
import dev.netvalve.stats.StatsCollector
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Creates and owns the *upstream* sockets that carry a flow to the real
 * internet. Every socket is [protected][SocketProtector] before connecting so
 * its packets bypass our own TUN. TCP connect latency is measured and fed to
 * [StatsCollector].
 *
 * These upstream sockets are the second half of the throttling pipeline: the
 * relay writes to [TcpUpstream.write] / [UdpUpstream.send] only after the token
 * bucket has granted allowance (see ThrottleManager / FlowSupervisor).
 */
class ConnectionManager(
    private val stats: StatsCollector,
    private val logger: Logger,
    private val connectTimeoutMillis: Int = 10_000,
    private val ipv4Memory: Ipv4PathMemory = Ipv4PathMemory(),
) {
    fun connectTcp(ctx: FlowContext, protector: SocketProtector): TcpUpstream {
        val destination = ctx.destinationAddress
        val canTranslate = Nat64.isEligible(destination)

        // An earlier IPv4 dial failed as unreachable and NAT64 worked: go to NAT64 first.
        if (canTranslate && ipv4Memory.preferNat64()) {
            try {
                return dialTcp(Nat64.synthesize(destination), ctx, protector)
            } catch (t: Throwable) {
                logger.d(LogCategory.ERROR, "NAT64 dial failed ${ctx.shortKey()}: ${t.message}; trying IPv4")
            }
        }

        return try {
            dialTcp(destination, ctx, protector)
        } catch (t: Throwable) {
            // Only "no route" errors lead to NAT64; refused connections and timeouts keep their old behaviour.
            if (!canTranslate || !Nat64.isUnreachable(t)) throw t
            val upstream = try {
                dialTcp(Nat64.synthesize(destination), ctx, protector)
            } catch (t2: Throwable) {
                logger.d(LogCategory.ERROR, "NAT64 fallback failed ${ctx.shortKey()}: ${t2.message}")
                throw t // report the original IPv4 error
            }
            if (ipv4Memory.markIpv4Broken()) {
                logger.i(LogCategory.ERROR, "IPv4 unreachable, using NAT64 (64:ff9b::/96) for IPv4 destinations; first: ${ctx.shortKey()}")
            }
            upstream
        }
    }

    /** One TCP dial to [destination] on the flow's port. The socket is closed again when the connect fails. */
    private fun dialTcp(destination: InetAddress, ctx: FlowContext, protector: SocketProtector): TcpUpstream {
        val socket = Socket()
        // java.net.Socket creates its OS socket lazily, so protect() sees no valid
        // file descriptor and returns false. Bind first to force the fd to exist.
        runCatching { socket.bind(InetSocketAddress(0)) }
        // Protect BEFORE connect so the SYN is not routed back into the tunnel.
        if (!protector.protect(socket)) {
            logger.w(LogCategory.ERROR, "protect() failed for ${ctx.shortKey()}")
        }
        val started = System.nanoTime()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(destination, ctx.destinationPort), connectTimeoutMillis)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
        val latencyMs = (System.nanoTime() - started) / 1_000_000
        stats.recordConnectLatency(latencyMs)
        return TcpUpstream(socket)
    }

    fun openUdp(ctx: FlowContext, protector: SocketProtector): UdpUpstream {
        val socket = DatagramSocket()
        if (!protector.protect(socket)) {
            logger.w(LogCategory.ERROR, "protect() failed for ${ctx.shortKey()}")
        }
        socket.connect(InetSocketAddress(ctx.destinationAddress, ctx.destinationPort))
        return UdpUpstream(socket)
    }
}

/** Blocking TCP upstream. Reads/writes are done on an IO dispatcher by the relay. */
class TcpUpstream(private val socket: Socket) {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    fun read(dst: ByteArray): Int = input.read(dst)

    fun write(src: ByteArray, off: Int, len: Int) {
        output.write(src, off, len)
        output.flush()
    }

    fun close() {
        runCatching { socket.close() }
    }
}

/** Blocking UDP upstream, connected to the flow's destination. */
class UdpUpstream(private val socket: DatagramSocket) {
    fun send(data: ByteArray) {
        socket.send(DatagramPacket(data, data.size))
    }

    /** @return payload bytes received, or null on timeout/close. */
    fun receive(bufferSize: Int = 64 * 1024): ByteArray? {
        val buf = ByteArray(bufferSize)
        val pkt = DatagramPacket(buf, buf.size)
        return try {
            socket.receive(pkt)
            buf.copyOf(pkt.length)
        } catch (_: Throwable) {
            null
        }
    }

    fun setTimeout(millis: Int) { socket.soTimeout = millis }

    fun close() {
        runCatching { socket.close() }
    }
}
