package dev.netvalve.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ServerChooserTest {

    private val v6 = InetAddress.getByName("2001:db8::1")
    private val v4 = InetAddress.getByName("192.0.2.1")
    private val other4 = InetAddress.getByName("198.51.100.7")

    @Test
    fun skipsADeadFamilyAndTakesTheOneThatAnswers() {
        val tried = ArrayList<InetAddress>()
        val c = ServerChooser(resolve = { listOf(v6, v4) }, canConnect = { a, _, _ -> tried += a; a == v4 })
        assertEquals("192.0.2.1", c.firstReachable("h.test", 80))
        assertEquals(listOf(v6, v4), tried) // IPv6 first (the resolver's order), then IPv4
    }

    @Test
    fun stopsAtTheFirstReachableAddress() {
        val tried = ArrayList<InetAddress>()
        val c = ServerChooser(resolve = { listOf(v4, v6) }, canConnect = { a, _, _ -> tried += a; true })
        assertEquals("192.0.2.1", c.firstReachable("h.test", 80))
        assertEquals(1, tried.size)
    }

    @Test
    fun nothingAnswersOrTheNameDoesNotResolveGivesNull() {
        assertNull(ServerChooser(resolve = { listOf(v4) }, canConnect = { _, _, _ -> false }).firstReachable("h.test", 80))
        assertNull(ServerChooser(resolve = { throw java.net.UnknownHostException("x") }, canConnect = { _, _, _ -> true }).firstReachable("h.test", 80))
        assertNull(ServerChooser(resolve = { listOf(v4) }, canConnect = { _, _, _ -> error("boom") }).firstReachable("h.test", 80))
    }

    @Test
    fun choosesTheFirstSourceThatAnswers() {
        val sources = listOf(
            DownloadSource("First", "http://first.test/a.bin"),
            DownloadSource("Second", "http://second.test:8080/b.bin"),
        )
        val ports = ArrayList<Int>()
        val c = ServerChooser(
            resolve = { host -> if (host == "first.test") listOf(v4) else listOf(other4) },
            canConnect = { a, port, _ -> ports += port; a == other4 },
        )
        val chosen = c.chooseDownload(sources)!!
        assertEquals("Second", chosen.source.name)
        assertEquals("198.51.100.7", chosen.address)
        assertEquals(listOf(80, 8080), ports)
    }

    @Test
    fun noSourceAnswersGivesNull() {
        val c = ServerChooser(resolve = { listOf(v4) }, canConnect = { _, _, _ -> false })
        assertNull(c.chooseDownload(DownloadSources.ALL))
    }

    @Test
    fun theDefaultSourcesAreTheOnesSeenWorkingInThisProject() {
        assertEquals(listOf("Linode Mumbai", "Tele2"), DownloadSources.ALL.map { it.name })
        assertTrue(DownloadSources.ALL.all { it.url.startsWith("http://") })
    }
}
