package dev.netvalve.payprobe

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * One probe = one brand-new connection and one HTTPS GET to a tiny endpoint (for example a
 * generate_204 URL), timed from before DNS and connect until the response has been read.
 *
 *  - `Connection: close` and no caching, so no probe can reuse the previous probe's connection.
 *  - The whole transaction is capped at [DEFAULT_TIMEOUT_MS]. Android's connect and read timeouts
 *    are per phase and do not cover DNS, so the request runs on a worker thread and is
 *    abandoned (and disconnected) when the total time is up.
 *  - The system DNS cache still applies, as it would for a real app, so after the first probe the
 *    DNS part is usually tiny. That is intended.
 *
 * Pure JVM (java.net only), so it is unit-tested against a local test server.
 */
object Prober {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    private const val MAX_BODY_BYTES = 64 * 1024

    fun probe(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ProbeResult {
        val start = System.nanoTime()
        val box = AtomicReference<ProbeResult?>(null)
        val connRef = AtomicReference<HttpURLConnection?>(null)
        val worker = Thread({ box.set(request(url, timeoutMs, start, connRef)) }, "payprobe-request")
        worker.isDaemon = true
        worker.start()
        worker.join(timeoutMs)
        if (worker.isAlive) {
            connRef.get()?.disconnect()
            worker.interrupt()
            return ProbeResult(timeoutMs, false, -1, "timeout")
        }
        return box.get() ?: ProbeResult(elapsedMs(start).coerceAtMost(timeoutMs), false, -1, "no_result")
    }

    private fun request(
        url: String,
        timeoutMs: Long,
        start: Long,
        connRef: AtomicReference<HttpURLConnection?>,
    ): ProbeResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "PayProbe/0.1")
            }
            connRef.set(conn)
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            if (stream != null) {
                stream.use { s ->
                    val buf = ByteArray(1024)
                    var total = 0
                    while (total < MAX_BODY_BYTES) {
                        val n = s.read(buf)
                        if (n < 0) break
                        total += n
                    }
                }
            }
            ProbeResult(elapsedMs(start).coerceAtMost(timeoutMs), code in 200..399, code, "")
        } catch (e: Exception) {
            // A connect or read timeout can fire just before the hard cap in probe() does. Both are
            // the same event for the data, so store them the same way: "timeout", at the cap.
            val latency = if (e is SocketTimeoutException) timeoutMs else elapsedMs(start).coerceAtMost(timeoutMs)
            ProbeResult(latency, false, -1, noteFor(e))
        } finally {
            conn?.disconnect()
        }
    }

    /** "timeout" for every kind of timeout, otherwise the exception class name. */
    internal fun noteFor(e: Exception): String =
        if (e is SocketTimeoutException) "timeout" else e.javaClass.simpleName

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L
}
