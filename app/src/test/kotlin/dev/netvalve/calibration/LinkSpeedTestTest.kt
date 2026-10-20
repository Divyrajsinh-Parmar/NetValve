package dev.netvalve.calibration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Runs the real socket code against a small local HTTP server that hands out data at a fixed speed. */
class LinkSpeedTestTest {

    /** Serves [statusLine] and then [bytesPerSec] of body for up to [seconds] seconds. */
    private fun server(statusLine: String, bytesPerSec: Int, seconds: Int): Pair<ServerSocket, Thread> {
        val ss = ServerSocket(0)
        val t = thread(isDaemon = true) {
            try {
                ss.accept().use { s ->
                    s.getInputStream().read(ByteArray(1024)) // the request
                    val out = s.getOutputStream()
                    out.write("$statusLine\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                    out.flush()
                    val chunk = ByteArray(bytesPerSec / 50) // 50 chunks per second
                    val end = System.nanoTime() + seconds * 1_000_000_000L
                    while (System.nanoTime() < end) {
                        out.write(chunk)
                        out.flush()
                        Thread.sleep(20)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return ss to t
    }

    @Test
    fun measuresAboutZeroPointNineTimesTheServedSpeed() = runBlocking {
        val (ss, _) = server("HTTP/1.1 200 OK", bytesPerSec = 2_000_000, seconds = 6)
        val rate = LinkSpeedTest(
            url = "http://127.0.0.1:${ss.localPort}/file.bin",
            durationMs = 2_500, discardMs = 700,
        ).measureDownload()
        ss.close()
        assertNotNull(rate)
        // served 2.0 MB/s, so R should be near 1.8 MB/s (generous window: timers on a busy machine)
        assertTrue("rate=$rate", rate!! in 1_200_000L..2_300_000L)
    }

    @Test
    fun anErrorStatusIsReportedAsFailure() {
        val (ss, _) = server("HTTP/1.1 403 Forbidden", bytesPerSec = 100_000, seconds = 2)
        var message: String? = null
        try {
            runBlocking {
                LinkSpeedTest(url = "http://127.0.0.1:${ss.localPort}/x", durationMs = 1_000, discardMs = 300)
                    .measureDownload()
            }
        } catch (e: IOException) {
            message = e.message
        }
        ss.close()
        assertTrue("message=$message", message != null && message.contains("403"))
    }

    @Test
    fun aServerThatIsNotThereIsAFailureNotAHang() {
        val ss = ServerSocket(0)
        val port = ss.localPort
        ss.close() // nobody listens any more: connection refused
        var failed = false
        try {
            runBlocking { LinkSpeedTest(url = "http://127.0.0.1:$port/x", durationMs = 1_000, discardMs = 300).measureDownload() }
        } catch (e: IOException) {
            failed = true
        }
        assertEquals(true, failed)
    }
}
