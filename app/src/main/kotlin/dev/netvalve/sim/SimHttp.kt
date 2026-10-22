package dev.netvalve.sim

import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.Socket
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal HTTP/1.1 over a raw socket for the traffic simulator.
 *
 * Why not HttpURLConnection: the roadmap wants separate connections, plain HTTP/1.1, no reuse.
 * A raw socket guarantees that: every call opens its own connection, sends `Connection: close`
 * and never offers HTTP/2. Pure JDK classes, no Android imports.
 */
internal object SimHttp {

    /**
     * [connectIps]: when not empty, the connection goes to these addresses (in this order) instead of looking [host]
     * up (the Host header and the TLS name stay [host]). The live demo gives the address it checked before (patch E1d).
     */
    class Target(
        val secure: Boolean,
        val host: String,
        val port: Int,
        val pathAndQuery: String,
        val connectIps: List<String> = emptyList(),
    )

    /**
     * Sent as User-Agent. The old "NetValve-Sim" got "403 Forbidden" from speed.cloudflare.com
     * (a bot check on unknown agents is the likely cause, not proven). Command-line speed tests
     * identify themselves like this one.
     */
    private const val USER_AGENT = "curl/8.5.0"

    /** @throws IllegalArgumentException for anything but a valid http or https URL. */
    fun parse(url: String, connectIps: List<String> = emptyList()): Target {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid URL: $url")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "URL must start with http:// or https://" }
        val host = uri.host
        require(!host.isNullOrEmpty()) { "URL has no host: $url" }
        val secure = scheme == "https"
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        return Target(secure, host, port, path, connectIps.filter { it.isNotBlank() })
    }

    fun getRequest(t: Target): ByteArray =
        ("GET ${t.pathAndQuery} HTTP/1.1\r\nHost: ${hostHeader(t)}\r\nUser-Agent: ${USER_AGENT}\r\n" +
            "Accept: */*\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)

    fun postHeader(t: Target, contentLength: Long): ByteArray =
        ("POST ${t.pathAndQuery} HTTP/1.1\r\nHost: ${hostHeader(t)}\r\nUser-Agent: ${USER_AGENT}\r\n" +
            "Content-Type: application/octet-stream\r\nContent-Length: $contentLength\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)

    private fun hostHeader(t: Target): String {
        val default = if (t.secure) 443 else 80
        return if (t.port == default) t.host else "${t.host}:${t.port}"
    }

    /**
     * Looks at the first bytes of an answer. Returns the status line when it is not 200 or 206,
     * and null when the answer is fine (or too short to judge).
     */
    fun badStatus(buf: ByteArray, len: Int): String? {
        if (len < 12) return null
        val text = String(buf, 0, minOf(len, 80), Charsets.ISO_8859_1)
        val line = text.substringBefore('\n').trimEnd('\r')
        val parts = line.split(' ')
        val code = if (parts.size >= 2) parts[1] else ""
        if (line.startsWith("HTTP/1.") && (code == "200" || code == "206")) return null
        return line.take(60)
    }

    /** First characters of the answer body (for example "error code: 1010"), or an empty string when there is none yet. */
    fun bodyHint(buf: ByteArray, len: Int): String {
        val text = String(buf, 0, len, Charsets.ISO_8859_1)
        val at = text.indexOf("\r\n\r\n")
        if (at < 0) return ""
        val body = text.substring(at + 4).take(60)
        return body.map { if (it.code < 32 || it.code > 126) ' ' else it }.joinToString("").trim()
    }

    /** Upper limit for one address while another one is still waiting (a broken IPv6 or IPv4 route must not use the whole time). */
    private const val PER_ADDRESS_MS = 4_000

    /**
     * The addresses in the order they are tried: IPv6 and IPv4 take turns, starting with the family the resolver put
     * first (the idea of "happy eyeballs"), so one dead family cannot block the other.
     */
    fun orderAddresses(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size < 2) return addresses
        val firstIsV6 = addresses.first() is Inet6Address
        val first = addresses.filter { (it is Inet6Address) == firstIsV6 }
        val second = addresses.filter { (it is Inet6Address) != firstIsV6 }
        val out = ArrayList<InetAddress>(addresses.size)
        for (i in 0 until maxOf(first.size, second.size)) {
            first.getOrNull(i)?.let { out.add(it) }
            second.getOrNull(i)?.let { out.add(it) }
        }
        return out
    }

    /** Tries the addresses one after the other; the first that connects wins. Throws the last error when none does. */
    private fun connectAny(t: Target, totalTimeoutMs: Int, resolver: (String) -> List<InetAddress>): Socket {
        val addresses = orderAddresses(
            if (t.connectIps.isNotEmpty()) t.connectIps.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() } else resolver(t.host),
        )
        if (addresses.isEmpty()) throw UnknownHostException(t.host)
        val deadline = System.nanoTime() + totalTimeoutMs * 1_000_000L
        var last: IOException? = null
        for ((i, address) in addresses.withIndex()) {
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
            if (remainingMs <= 0) break
            val perAddress = if (addresses.size - i > 1) minOf(remainingMs, PER_ADDRESS_MS) else remainingMs
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, t.port), perAddress)
                return socket
            } catch (e: IOException) {
                runCatching { socket.close() }
                last = e
            }
        }
        throw last ?: SocketTimeoutException("connect to ${t.host} timed out")
    }

    /** Opens a NEW connection (TLS for https, with hostname check). The caller closes it. */
    fun open(
        t: Target,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
        resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): Socket {
        val plain = connectAny(t, connectTimeoutMs, resolver)
        plain.soTimeout = readTimeoutMs
        if (!t.secure) return plain
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, t.host, t.port, true) as SSLSocket
        try {
            ssl.soTimeout = readTimeoutMs
            ssl.startHandshake()
            val ok = HttpsURLConnection.getDefaultHostnameVerifier().verify(t.host, ssl.session)
            if (!ok) throw javax.net.ssl.SSLPeerUnverifiedException("hostname ${t.host} not verified")
        } catch (e: Exception) {
            runCatching { ssl.close() }
            throw e
        }
        return ssl
    }
}
