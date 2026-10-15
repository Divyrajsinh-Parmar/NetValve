package dev.netvalve.payprobe

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayProbeCoreTest {

    private lateinit var server: TinyHttpServer
    private val baseUrl get() = "http://127.0.0.1:${server.port}"

    @Before
    fun startServer() {
        server = TinyHttpServer().also { it.start() }
    }

    @After
    fun stopServer() = server.stop()

    private fun sample(i: Int, ms: Long, ok: Boolean = true) =
        ProbeSample(i, 1_000L * i, 1_000L * i, ms, ok, if (ok) 204 else -1, if (ok) "" else "timeout")

    // ---- statistics ---------------------------------------------------------------------

    @Test
    fun percentilesUseNearestRank() {
        val sorted = (1L..100L).toList()
        assertEquals(50L, ProbeStats.percentile(sorted, 50.0))
        assertEquals(95L, ProbeStats.percentile(sorted, 95.0))
        assertEquals(99L, ProbeStats.percentile(sorted, 99.0))
        assertEquals(100L, ProbeStats.percentile(sorted, 100.0))
        assertEquals(0L, ProbeStats.percentile(emptyList(), 95.0))
        assertEquals(7L, ProbeStats.percentile(listOf(7L), 99.0))
    }

    @Test
    fun summaryCountsFailuresAndKeepsThemInThePercentiles() {
        val samples = (1..19).map { sample(it, 20L) } + sample(20, 5_000L, ok = false)
        val s = ProbeStats.summarize(samples)
        assertEquals(20, s.count)
        assertEquals(1, s.failures)
        assertEquals(20L, s.p50)
        assertEquals(20L, s.p95)       // 19 of 20 samples are 20 ms, nearest rank 19
        assertEquals(5_000L, s.p99)    // the timeout shows up in the tail
        assertEquals(5_000L, s.max)
        assertEquals(ProbeSummary.EMPTY, ProbeStats.summarize(emptyList()))
    }

    // ---- CSV ----------------------------------------------------------------------------

    @Test
    fun csvRowsAndEscaping() {
        assertEquals("1970-01-01T00:00:00Z", CsvFormat.isoTime(0))
        assertEquals("a", CsvFormat.escape("a"))
        assertEquals("\"a,b\"", CsvFormat.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvFormat.escape("say \"hi\""))
        assertEquals("1970-01-01T00:00:01Z,1000,25,1,204,C1_S1,", CsvFormat.row(sample(1, 25), "C1_S1"))
        assertEquals("C1_S1_run_3", CsvFormat.safeLabel("C1 S1/run:3"))
        assertEquals("run", CsvFormat.safeLabel("   "))
        assertEquals("run", CsvFormat.safeLabel(null))
        assertEquals(40, CsvFormat.safeLabel("x".repeat(100)).length)
    }

    @Test
    fun csvSinkFlushesEveryRow() {
        val f = File.createTempFile("payprobe", ".csv")
        val sink = CsvSink(f, "t")
        sink.write(sample(0, 10))
        sink.write(sample(1, 20))
        val lines = f.readLines()      // read while the file is still open
        sink.close()
        f.delete()
        assertEquals(CsvFormat.HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[2].contains(",20,1,204,t,"))
    }

    // ---- prober against a local server ----------------------------------------------------

    @Test
    fun okResponseIsFastAndOk() {
        val r = Prober.probe("$baseUrl/ok", 2_000)
        assertTrue(r.ok); assertEquals(204, r.httpCode); assertEquals("", r.note)
        assertTrue("took ${r.latencyMs}", r.latencyMs < 1_000)
    }

    @Test
    fun serverErrorIsAFailureButHasACode() {
        val r = Prober.probe("$baseUrl/boom", 2_000)
        assertFalse(r.ok); assertEquals(500, r.httpCode)
    }

    @Test
    fun hungServerIsCutOffAtTheTimeoutAndStoredAsTheCap() {
        val t0 = System.nanoTime()
        val r = Prober.probe("$baseUrl/slow", 400)
        val wall = (System.nanoTime() - t0) / 1_000_000
        assertFalse(r.ok)
        assertEquals("note=${r.note} latency=${r.latencyMs} wall=$wall ms", "timeout", r.note)
        assertEquals("note=${r.note} latency=${r.latencyMs} wall=$wall ms", 400L, r.latencyMs)
        assertTrue("returned after $wall ms", wall in 350..1_200)
    }

    @Test
    fun everyKindOfTimeoutIsNotedAsTimeout() {
        assertEquals("timeout", Prober.noteFor(java.net.SocketTimeoutException("Read timed out")))
        assertEquals("timeout", Prober.noteFor(java.net.SocketTimeoutException("connect timed out")))
        assertEquals("ConnectException", Prober.noteFor(java.net.ConnectException("refused")))
    }

    @Test
    fun refusedConnectionFailsFast() {
        val port = server.port
        server.stop()
        val r = Prober.probe("http://127.0.0.1:$port/ok", 2_000)
        assertFalse(r.ok); assertEquals(-1, r.httpCode)
        assertTrue(r.note.isNotEmpty())
        assertTrue("took ${r.latencyMs}", r.latencyMs < 2_000)
        server = TinyHttpServer().also { it.start() } // for @After
    }

    // ---- shared state -------------------------------------------------------------------------

    @Test
    fun probeStateKeepsSamplesAndClearsOnlyWhenStopped() {
        ProbeState.begin("u", "l", "/tmp/x.csv", 5L)
        assertTrue(ProbeState.running)
        (0..4).forEach { ProbeState.add(sample(it, 10L + it)) }
        assertEquals(5, ProbeState.all().size)
        assertEquals(listOf(13L, 14L), ProbeState.last(2).map { it.latencyMs })
        ProbeState.clear()                 // ignored while running
        assertEquals(5, ProbeState.all().size)
        ProbeState.end()
        ProbeState.clear()
        assertEquals(0, ProbeState.all().size)
    }
}

/**
 * Tiny HTTP/1.1 server on a plain ServerSocket (loopback only). The JDK's com.sun.net.httpserver is
 * not on the Android Gradle plugin's unit-test classpath, so the tests cannot use it.
 * Paths: /ok answers 204, /boom answers 500, /slow waits 3 s and then answers 204.
 */
private class TinyHttpServer {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = serverSocket.localPort

    @Volatile
    private var stopped = false

    fun start() {
        Thread {
            while (!stopped) {
                val client = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    break
                }
                Thread { handle(client) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        stopped = true
        try {
            serverSocket.close()
        } catch (_: IOException) {
        }
    }

    private fun handle(client: Socket) {
        try {
            client.use {
                val reader = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = reader.readLine() ?: return
                while (true) { // read and ignore the request headers
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val path = requestLine.split(' ').getOrNull(1) ?: "/"
                if (path.startsWith("/slow")) Thread.sleep(3_000)
                val status = if (path.startsWith("/boom")) "500 Internal Server Error" else "204 No Content"
                it.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    flush()
                }
            }
        } catch (_: Exception) {
            // client gave up (timeout test) or the server was stopped: nothing to do
        }
    }
}
