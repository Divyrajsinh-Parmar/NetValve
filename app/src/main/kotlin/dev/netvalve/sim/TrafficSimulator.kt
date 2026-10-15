package dev.netvalve.sim

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

enum class SimMode { DOWN, UP, BOTH }

/** Live numbers for the simulator screen. Rates are bytes per second over the last second. */
data class SimStats(
    val running: Boolean = false,
    val mode: SimMode? = null,
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val elapsedSec: Int = 0,
    val errors: Int = 0,
    val lastError: String? = null,
)

/**
 * The "Low app" of the evaluation (roadmap M5): generates heavy traffic under NetValve's own UID,
 * which the tier rules lock to Low.
 *
 * Down: [downloads] parallel GETs, each a separate HTTP/1.1 connection that is reopened when it ends.
 * Up: [uploads] parallel POSTs of random bytes, each a separate connection.
 * Stops by itself after the auto-stop time (0 = never) so a forgotten run cannot eat a data pack.
 *
 * Pure Kotlin plus coroutines: no Android imports, so it is tested on a JVM against a local server.
 * It runs in [scope] (the app scope) so the load keeps going when the screen is left.
 */
class TrafficSimulator(
    private val scope: CoroutineScope,
    private val downloads: Int = DEFAULT_DOWNLOADS,
    private val uploads: Int = DEFAULT_UPLOADS,
) {
    private val _stats = MutableStateFlow(SimStats())
    val stats: StateFlow<SimStats> = _stats

    private val downBytes = AtomicLong(0)
    private val upBytes = AtomicLong(0)
    private val errors = AtomicInteger(0)
    @Volatile private var lastError: String? = null
    @Volatile private var running = false
    private var job: Job? = null
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    /**
     * @throws IllegalArgumentException when a needed URL is invalid (nothing is started then).
     */
    @Synchronized
    fun start(mode: SimMode, downloadUrl: String, uploadUrl: String, autoStopSec: Int) {
        val down = if (mode != SimMode.UP) SimHttp.parse(downloadUrl) else null
        val up = if (mode != SimMode.DOWN) SimHttp.parse(uploadUrl) else null
        stopInternal()

        downBytes.set(0); upBytes.set(0); errors.set(0); lastError = null
        running = true
        _stats.value = SimStats(running = true, mode = mode)

        job = scope.launch(Dispatchers.IO) {
            if (down != null) repeat(downloads) { launch { downloadLoop(down) } }
            if (up != null) repeat(uploads) { launch { uploadLoop(up) } }
            sampleLoop(mode, autoStopSec)
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
        _stats.value = _stats.value.copy(running = false, downBps = 0, upBps = 0)
    }

    private fun stopInternal() {
        running = false
        job?.cancel()
        job = null
        // Blocking reads are not cancellable: closing the sockets makes them throw and end.
        // Plain iteration on purpose: toList() can throw NoSuchElementException when a worker
        // removes its socket from this concurrent set at the same moment.
        for (s in sockets) runCatching { s.close() }
        sockets.clear()
    }

    private suspend fun sampleLoop(mode: SimMode, autoStopSec: Int) {
        var lastDown = 0L
        var lastUp = 0L
        var seconds = 0
        while (scope.isActive && running) {
            delay(1_000)
            seconds++
            val d = downBytes.get()
            val u = upBytes.get()
            _stats.value = SimStats(
                running = true, mode = mode,
                downBps = d - lastDown, upBps = u - lastUp,
                totalDown = d, totalUp = u,
                elapsedSec = seconds, errors = errors.get(), lastError = lastError,
            )
            lastDown = d
            lastUp = u
            if (autoStopSec > 0 && seconds >= autoStopSec) {
                stop()
                return
            }
        }
    }

    private suspend fun downloadLoop(t: SimHttp.Target) {
        val buf = ByteArray(BUFFER)
        while (running) {
            var s: Socket? = null
            try {
                s = SimHttp.open(t)
                sockets.add(s)
                s.getOutputStream().apply { write(SimHttp.getRequest(t)); flush() }
                val input = s.getInputStream()
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) break
                    downBytes.addAndGet(n.toLong())
                }
            } catch (e: IOException) {
                if (running) noteError(e)
            } finally {
                s?.let { sockets.remove(it); runCatching { it.close() } }
            }
            if (running) delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun uploadLoop(t: SimHttp.Target) {
        val chunk = ByteArray(BUFFER).also { Random.nextBytes(it) }
        while (running) {
            var s: Socket? = null
            try {
                s = SimHttp.open(t)
                sockets.add(s)
                val out = s.getOutputStream()
                out.write(SimHttp.postHeader(t, UPLOAD_BODY_BYTES))
                var sent = 0L
                while (running && sent < UPLOAD_BODY_BYTES) {
                    val n = minOf(chunk.size.toLong(), UPLOAD_BODY_BYTES - sent).toInt()
                    out.write(chunk, 0, n)
                    sent += n
                    upBytes.addAndGet(n.toLong())
                }
                out.flush()
            } catch (e: IOException) {
                if (running) noteError(e)
            } finally {
                s?.let { sockets.remove(it); runCatching { it.close() } }
            }
            if (running) delay(RETRY_DELAY_MS)
        }
    }

    private fun noteError(e: Exception) {
        errors.incrementAndGet()
        lastError = "${e.javaClass.simpleName}: ${e.message}"
    }

    companion object {
        const val DEFAULT_DOWNLOADS = 3
        const val DEFAULT_UPLOADS = 2
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=100000000"
        const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"
        const val DEFAULT_AUTO_STOP_SEC = 60
        private const val BUFFER = 16 * 1024
        private const val UPLOAD_BODY_BYTES = 25L * 1024 * 1024
        private const val RETRY_DELAY_MS = 500L
    }
}
