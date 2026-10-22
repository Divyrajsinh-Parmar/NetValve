package dev.netvalve.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One probe = a new TCP connection to [ip]:[port], a tiny HTTP GET, and the time until the first byte
 * of the answer ("HTTP/1."). No answer within [timeoutMs] (or any error) = lost = null.
 * A raw socket on purpose: every probe is its own connection (its own flow in the relay).
 * Pure JDK, no Android imports.
 */
class HttpProbe(
    private val ip: String,
    private val host: String,
    private val port: Int = 80,
    private val path: String = "/generate_204",
    private val timeoutMs: Int = 3_000,
) : Prober {

    override suspend fun probeOnce(): Long? = withContext(Dispatchers.IO) { probeBlocking() }

    private fun probeBlocking(): Long? {
        val started = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - started) / 1_000_000L
        var socket: Socket? = null
        return try {
            val s = Socket()
            socket = s
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            val left = timeoutMs - elapsedMs()
            if (left <= 0L) return null
            s.soTimeout = left.toInt()
            s.getOutputStream().apply {
                write("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: curl/8.5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val head = ByteArray(7)
            var got = 0
            val input = s.getInputStream()
            while (got < head.size) {
                val n = input.read(head, got, head.size - got)
                if (n < 0) return null
                got += n
            }
            if (String(head, Charsets.US_ASCII) != "HTTP/1.") return null
            val ms = elapsedMs()
            if (ms > timeoutMs) null else ms
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}
