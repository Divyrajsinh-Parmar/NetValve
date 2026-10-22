package dev.netvalve.demo

import dev.netvalve.calibration.RatePolicy
import dev.netvalve.sim.SimHttp
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/** A server the demo may download from. Both sources were seen working in this project (see SESSION_LOG). */
data class DownloadSource(val name: String, val url: String)

data class ChosenDownload(val source: DownloadSource, val address: String)

object DownloadSources {
    val ALL: List<DownloadSource> = listOf(
        DownloadSource("Linode Mumbai", RatePolicy.DOWNLOAD_URL),
        DownloadSource("Tele2", "http://speedtest.tele2.net/100MB.zip"),
    )
}

/**
 * Checks, BEFORE the demo changes anything, which test servers really answer from this network now, so the
 * demo does not run (and restart the tunnel) for nothing. A server counts as reachable when a TCP connection
 * to its port is accepted within [CHECK_TIMEOUT_MS]. IPv6 and IPv4 addresses are tried in turn, so a dead
 * family is skipped. Pure Kotlin, the lookups and connects can be replaced in tests.
 */
class ServerChooser(
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val canConnect: (InetAddress, Int, Int) -> Boolean = ::tcpConnects,
) {
    /** The first address of [host] that accepts a connection on [port], or null. */
    fun firstReachable(host: String, port: Int, timeoutMs: Int = CHECK_TIMEOUT_MS): String? {
        val addresses = try {
            SimHttp.orderAddresses(resolve(host))
        } catch (_: Exception) {
            return null
        }
        for (address in addresses) {
            if (runCatching { canConnect(address, port, timeoutMs) }.getOrDefault(false)) return address.hostAddress
        }
        return null
    }

    /** The first source of [sources] with a reachable address, or null when none answers. */
    fun chooseDownload(sources: List<DownloadSource>): ChosenDownload? {
        for (source in sources) {
            val uri = runCatching { URI(source.url) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            val port = if (uri.port > 0) uri.port else 80
            val address = firstReachable(host, port) ?: continue
            return ChosenDownload(source, address)
        }
        return null
    }

    companion object {
        const val CHECK_TIMEOUT_MS = 3_000

        fun tcpConnects(address: InetAddress, port: Int, timeoutMs: Int): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }
            true
        } catch (_: IOException) {
            false
        }
    }
}
