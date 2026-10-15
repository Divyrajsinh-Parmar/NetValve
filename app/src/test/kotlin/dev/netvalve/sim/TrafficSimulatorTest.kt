package dev.netvalve.sim

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Runs the simulator against a tiny local HTTP server (loopback only, no internet needed). */
class TrafficSimulatorTest {

    /** Answers GET with an endless body and swallows POST bodies; records connection counts. */
    private class LocalServer : AutoCloseable {
        val server = ServerSocket(0)
        val accepted = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val received = AtomicLong()
        val gets = AtomicInteger()
        val posts = AtomicInteger()
        private val thread = Thread {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (e: Exception) { break }
                Thread { handle(s) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true; start() }

        val url: String get() = "http://127.0.0.1:${server.localPort}/x"

        private fun handle(s: Socket) {
            accepted.incrementAndGet()
            val now = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            try {
                val inp = s.getInputStream()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val b = inp.read()
                    if (b < 0) return
                    head.append(b.toChar())
                }
                if (head.startsWith("GET")) {
                    gets.incrementAndGet()
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray())
                    val chunk = ByteArray(32 * 1024)
                    while (true) out.write(chunk)
                } else {
                    posts.incrementAndGet()
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        received.addAndGet(n.toLong())
                    }
                }
            } catch (_: Exception) {
            } finally {
                active.decrementAndGet()
                runCatching { s.close() }
            }
        }

        override fun close() { runCatching { server.close() } }
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun waitUntil(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!cond() && System.nanoTime() < end) delay(20)
    }

    @Test
    fun parseHandlesDefaultsAndRejectsBadUrls() {
        val https = SimHttp.parse("https://speed.cloudflare.com/__down?bytes=100000000")
        assertTrue(https.secure)
        assertEquals(443, https.port)
        assertEquals("/__down?bytes=100000000", https.pathAndQuery)
        assertEquals(80, SimHttp.parse("http://example.org").port)
        assertEquals("/", SimHttp.parse("http://example.org").pathAndQuery)
        assertEquals(8080, SimHttp.parse("http://127.0.0.1:8080/a").port)
        for (bad in listOf("", "ftp://x.org/a", "not a url", "http:///nohost")) {
            try {
                SimHttp.parse(bad)
                fail("expected IllegalArgumentException for '$bad'")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun requestsAreHttp11WithConnectionClose() {
        val t = SimHttp.parse("https://h.example/p?q=1")
        val get = String(SimHttp.getRequest(t))
        assertTrue(get.startsWith("GET /p?q=1 HTTP/1.1\r\nHost: h.example\r\n"))
        assertTrue("Connection: close" in get && get.endsWith("\r\n\r\n"))
        val post = String(SimHttp.postHeader(t, 123))
        assertTrue(post.startsWith("POST /p?q=1 HTTP/1.1") && "Content-Length: 123" in post)
    }

    @Test
    fun invalidUrlStartsNothing() {
        val scope = scope()
        try {
            val sim = TrafficSimulator(scope)
            try {
                sim.start(SimMode.DOWN, "nope", "nope", 0)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            assertFalse(sim.stats.value.running)
        } finally { scope.cancel() }
    }

    @Test
    fun downloadUsesThreeParallelConnectionsAndCountsBytes() {
        val scope = scope()
        LocalServer().use { server ->
            try {
                val sim = TrafficSimulator(scope)
                sim.start(SimMode.DOWN, server.url, server.url, 0)
                runBlocking { waitUntil(5_000) { sim.stats.value.elapsedSec >= 2 } }
                val s = sim.stats.value
                assertTrue("running", s.running)
                assertTrue("totalDown=${s.totalDown}", s.totalDown > 100_000)
                assertTrue("downBps=${s.downBps}", s.downBps > 0)
                assertEquals(0L, s.totalUp)
                assertEquals("parallel connections", 3, server.peak.get())
                sim.stop()
                assertFalse(sim.stats.value.running)
                runBlocking { waitUntil(3_000) { server.active.get() == 0 } }
                assertEquals("sockets closed on stop", 0, server.active.get())
            } finally { scope.cancel() }
        }
    }

    @Test
    fun uploadUsesTwoParallelConnectionsAndSendsBodies() {
        val scope = scope()
        LocalServer().use { server ->
            try {
                val sim = TrafficSimulator(scope)
                sim.start(SimMode.UP, server.url, server.url, 0)
                runBlocking { waitUntil(5_000) { sim.stats.value.elapsedSec >= 2 } }
                val s = sim.stats.value
                assertTrue("totalUp=${s.totalUp}", s.totalUp > 100_000)
                assertEquals(0L, s.totalDown)
                assertEquals(2, server.peak.get())
                assertTrue(server.posts.get() >= 2 && server.gets.get() == 0)
                sim.stop()
            } finally { scope.cancel() }
        }
    }

    @Test
    fun bothModeRunsFiveConnections() {
        val scope = scope()
        LocalServer().use { server ->
            try {
                val sim = TrafficSimulator(scope)
                sim.start(SimMode.BOTH, server.url, server.url, 0)
                runBlocking { waitUntil(5_000) { sim.stats.value.elapsedSec >= 2 } }
                assertEquals(5, server.peak.get())
                sim.stop()
            } finally { scope.cancel() }
        }
    }

    @Test
    fun autoStopEndsTheRun() {
        val scope = scope()
        LocalServer().use { server ->
            try {
                val sim = TrafficSimulator(scope)
                sim.start(SimMode.DOWN, server.url, server.url, 2)
                runBlocking { waitUntil(6_000) { !sim.stats.value.running } }
                assertFalse("auto-stop should have fired", sim.stats.value.running)
                assertTrue(sim.stats.value.totalDown > 0)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun connectionFailureIsCountedNotThrown() {
        val scope = scope()
        try {
            val dead = ServerSocket(0).also { it.close() } // port that nobody listens on
            val sim = TrafficSimulator(scope)
            sim.start(SimMode.DOWN, "http://127.0.0.1:${dead.localPort}/x", "http://127.0.0.1/", 0)
            runBlocking { waitUntil(5_000) { sim.stats.value.errors > 0 || sim.stats.value.elapsedSec >= 2 } }
            runBlocking { waitUntil(3_000) { sim.stats.value.elapsedSec >= 1 } }
            assertTrue("errors=${sim.stats.value.errors}", sim.stats.value.errors > 0)
            sim.stop()
        } finally { scope.cancel() }
    }
}
