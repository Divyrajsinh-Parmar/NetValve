package dev.netvalve.sim

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun connectIpsAreUsedInsteadOfTheNameLookup() {
        val scope = scope()
        LocalServer().use { server ->
            try {
                // the host name cannot be resolved; only the given address can work (live demo, patch E1d)
                val url = "http://download.invalid:${server.server.localPort}/x"
                val t = SimHttp.parse(url, listOf("127.0.0.1"))
                assertEquals("download.invalid", t.host)
                assertEquals(listOf("127.0.0.1"), t.connectIps)
                assertEquals(emptyList<String>(), SimHttp.parse(url).connectIps)
                assertEquals(emptyList<String>(), SimHttp.parse(url, listOf(" ")).connectIps)
                val sim = TrafficSimulator(scope)
                sim.start(SimMode.DOWN, url, url, 0, downloadConnectIps = listOf("127.0.0.1"))
                runBlocking { waitUntil(5_000) { sim.stats.value.totalDown > 0 } }
                sim.stop()
                assertTrue("down ${sim.stats.value.totalDown} errors ${sim.stats.value.errors}", sim.stats.value.totalDown > 0)
                assertTrue(server.gets.get() >= 1)

                // without the address the same URL fails (a name that does not exist)
                val sim2 = TrafficSimulator(scope)
                sim2.start(SimMode.DOWN, url, url, 0)
                runBlocking { waitUntil(5_000) { sim2.stats.value.errors > 0 } }
                sim2.stop()
                assertTrue(sim2.stats.value.errors > 0)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun addressesAreOrderedWithBothFamiliesInTurn() {
        val v6a = java.net.InetAddress.getByName("2001:db8::1")
        val v6b = java.net.InetAddress.getByName("2001:db8::2")
        val v4a = java.net.InetAddress.getByName("192.0.2.1")
        val v4b = java.net.InetAddress.getByName("192.0.2.2")
        assertEquals(listOf(v6a, v4a, v6b, v4b), SimHttp.orderAddresses(listOf(v6a, v6b, v4a, v4b)))
        assertEquals(listOf(v4a, v6a, v4b), SimHttp.orderAddresses(listOf(v4a, v4b, v6a)))
        assertEquals(listOf(v4a), SimHttp.orderAddresses(listOf(v4a)))
        assertEquals(emptyList<java.net.InetAddress>(), SimHttp.orderAddresses(emptyList()))
    }

    @Test
    fun openFallsBackToTheNextAddressWhenTheFirstOneFails() {
        // nothing listens on the IPv6 loopback port, the IPv4 loopback one accepts
        val server = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        try {
            val t = SimHttp.parse("http://h.test:${server.localPort}/x")
            val resolver = { _: String ->
                listOf(java.net.InetAddress.getByName("::1"), java.net.InetAddress.getByName("127.0.0.1"))
            }
            SimHttp.open(t, connectTimeoutMs = 5_000, resolver = resolver).use { s ->
                assertEquals("127.0.0.1", (s.remoteSocketAddress as java.net.InetSocketAddress).address.hostAddress)
            }
        } finally { server.close() }
    }

    @Test
    fun openThrowsWhenNoAddressWorks() {
        val closed = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).also { it.close() }
        val t = SimHttp.parse("http://h.test:${closed.localPort}/x")
        try {
            SimHttp.open(t, connectTimeoutMs = 2_000, resolver = { listOf(java.net.InetAddress.getByName("127.0.0.1")) }).close()
            fail("expected an IOException")
        } catch (_: java.io.IOException) {
        }
        try {
            SimHttp.open(t, connectTimeoutMs = 2_000, resolver = { emptyList() }).close()
            fail("expected UnknownHostException")
        } catch (_: java.net.UnknownHostException) {
        }
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
    fun repeatedStartAndStopNeverThrows() {
        // Regression: stop() used toList() on a concurrent set and could throw NoSuchElementException
        // while workers removed their sockets. A stress run, so it is bounded to about 3 seconds.
        val scope = scope()
        LocalServer().use { server ->
            try {
                val sim = TrafficSimulator(scope)
                repeat(100) { i ->
                    sim.start(if (i % 2 == 0) SimMode.DOWN else SimMode.BOTH, server.url, server.url, 0)
                    Thread.sleep(5L + (i % 7) * 4)
                    sim.stop()
                }
                assertFalse(sim.stats.value.running)
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
    fun statusLineCheck() {
        fun bad(s: String) = SimHttp.badStatus(s.toByteArray(), s.length)
        assertNull(bad("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n"))
        assertNull(bad("HTTP/1.1 206 Partial Content\r\n\r\n"))
        assertNull(bad("HTTP"))
        assertEquals("HTTP/1.1 403 Forbidden", bad("HTTP/1.1 403 Forbidden\r\nServer: x\r\n\r\n"))
        assertEquals("HTTP/1.1 429 Too Many Requests", bad("HTTP/1.1 429 Too Many Requests\r\n\r\n"))
    }

    @Test
    fun bodyHintShowsTheStartOfTheBody() {
        val a = "HTTP/1.1 403 Forbidden\r\nServer: x\r\n\r\nerror code: 1010"
        assertEquals("error code: 1010", SimHttp.bodyHint(a.toByteArray(), a.length))
        val b = "HTTP/1.1 403 Forbidden\r\nServer: x\r\n\r\n"
        assertEquals("", SimHttp.bodyHint(b.toByteArray(), b.length))
        val c = "HTTP/1.1 403 Forbidden\r\nServer: x"
        assertEquals("", SimHttp.bodyHint(c.toByteArray(), c.length))
    }

    @Test
    fun nonOkAnswerIsAnErrorNotADownload() {
        val server = ServerSocket(0)
        Thread {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (e: Exception) { break }
                try {
                    val inp = s.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) {
                        val b = inp.read()
                        if (b < 0) break
                        head.append(b.toChar())
                    }
                    s.getOutputStream().apply {
                        write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { s.close() }
                }
            }
        }.apply { isDaemon = true; start() }
        val scope = scope()
        try {
            val sim = TrafficSimulator(scope)
            sim.start(SimMode.DOWN, "http://127.0.0.1:${server.localPort}/x", "http://127.0.0.1/", 0)
            runBlocking { waitUntil(5_000) { sim.stats.value.errors > 0 } }
            val st = sim.stats.value
            sim.stop()
            assertTrue("errors=${st.errors}", st.errors > 0)
            assertTrue("lastError=${st.lastError}", st.lastError?.contains("403") == true)
            assertEquals(0L, st.totalDown)
        } finally {
            runCatching { server.close() }
            scope.cancel()
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
