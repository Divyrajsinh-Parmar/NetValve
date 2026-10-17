package dev.netvalve.sim

import java.net.InetSocketAddress
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

    class Target(val secure: Boolean, val host: String, val port: Int, val pathAndQuery: String)

    /**
     * Sent as User-Agent. The old "NetValve-Sim" got "403 Forbidden" from speed.cloudflare.com
     * (a bot check on unknown agents is the likely cause, not proven). Command-line speed tests
     * identify themselves like this one.
     */
    private const val USER_AGENT = "curl/8.5.0"

    /** @throws IllegalArgumentException for anything but a valid http or https URL. */
    fun parse(url: String): Target {
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
        return Target(secure, host, port, path)
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

    /** Opens a NEW connection (TLS for https, with hostname check). The caller closes it. */
    fun open(t: Target, connectTimeoutMs: Int = 10_000, readTimeoutMs: Int = 15_000): Socket {
        val plain = Socket()
        plain.tcpNoDelay = true
        plain.connect(InetSocketAddress(t.host, t.port), connectTimeoutMs)
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
