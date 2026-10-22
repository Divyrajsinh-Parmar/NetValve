package dev.netvalve.demo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class HttpProbeTest {

    private fun server(reply: ((java.net.Socket) -> Unit)): Pair<ServerSocket, Thread> {
        val ss = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        val t = thread(isDaemon = true) {
            try {
                while (!ss.isClosed) {
                    val c = ss.accept()
                    thread(isDaemon = true) { runCatching { reply(c) } }
                }
            } catch (_: Exception) { }
        }
        return ss to t
    }

    @Test
    fun answersWithTheTimeOfTheFirstByte() {
        val (ss, _) = server { c ->
            c.getInputStream().read(ByteArray(512))
            Thread.sleep(80)
            c.getOutputStream().apply { write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray()); flush() }
            c.close()
        }
        try {
            val ms = runBlocking { HttpProbe("127.0.0.1", "test", ss.localPort, timeoutMs = 2_000).probeOnce() }
            assertNotNull(ms)
            assertTrue("ms $ms", ms!! in 70..1_000)
        } finally { ss.close() }
    }

    @Test
    fun silentServerIsLostAfterTheTimeout() {
        val (ss, _) = server { c -> Thread.sleep(3_000); c.close() }
        try {
            val t0 = System.nanoTime()
            val ms = runBlocking { HttpProbe("127.0.0.1", "test", ss.localPort, timeoutMs = 300).probeOnce() }
            assertNull(ms)
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000)
        } finally { ss.close() }
    }

    @Test
    fun wrongAnswerAndRefusedConnectionAreLost() {
        val (ss, _) = server { c ->
            c.getInputStream().read(ByteArray(512))
            c.getOutputStream().apply { write("garbage!".toByteArray()); flush() }
            c.close()
        }
        val port = ss.localPort
        try {
            assertNull(runBlocking { HttpProbe("127.0.0.1", "test", port, timeoutMs = 500).probeOnce() })
        } finally { ss.close() }
        assertNull(runBlocking { HttpProbe("127.0.0.1", "test", port, timeoutMs = 500).probeOnce() })
    }
}
