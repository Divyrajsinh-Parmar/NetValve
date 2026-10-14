package dev.netvalve.payprobe

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayProbeCoreTest {

    private lateinit var server: HttpServer
    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex -> ex.sendResponseHeaders(204, -1); ex.close() }
        server.createContext("/boom") { ex -> ex.sendResponseHeaders(500, -1); ex.close() }
        server.createContext("/slow") { ex -> Thread.sleep(3_000); ex.sendResponseHeaders(204, -1); ex.close() }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

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
        assertFalse(r.ok); assertEquals("timeout", r.note); assertEquals(400L, r.latencyMs)
        assertTrue("returned after $wall ms", wall in 350..1_200)
    }

    @Test
    fun refusedConnectionFailsFast() {
        val port = server.address.port
        server.stop(0)
        val r = Prober.probe("http://127.0.0.1:$port/ok", 2_000)
        assertFalse(r.ok); assertEquals(-1, r.httpCode)
        assertTrue(r.note.isNotEmpty())
        assertTrue("took ${r.latencyMs}", r.latencyMs < 2_000)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() } // for @After
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
