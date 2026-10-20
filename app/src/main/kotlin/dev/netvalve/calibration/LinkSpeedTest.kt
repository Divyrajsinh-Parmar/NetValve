package dev.netvalve.calibration

import dev.netvalve.scheduler.CalibratorMath
import dev.netvalve.sim.SimHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * One download speed test over its own plain socket (the HTTP client of the Load simulator is
 * reused), short and bounded: connect 8 s, no data for 5 s = failure, total about [durationMs].
 *
 * Pure JDK plus coroutines: no Android classes.
 */
class LinkSpeedTest(
    private val url: String = RatePolicy.DOWNLOAD_URL,
    private val durationMs: Long = RatePolicy.MEASURE_MS,
    private val discardMs: Long = RatePolicy.DISCARD_MS,
) {
    /**
     * @return the pacer rate R in bytes/s (0.9 x the steady download speed), or null when the
     *         measurement is unusable (too short, no data). Throws [IOException] for connection or
     *         HTTP errors; the caller treats every failure the same way (keep the last known value).
     */
    suspend fun measureDownload(): Long? = withContext(Dispatchers.IO) {
        val target = SimHttp.parse(url)
        SimHttp.open(target, connectTimeoutMs = 8_000, readTimeoutMs = 5_000).use { socket ->
            val out = socket.getOutputStream()
            out.write(SimHttp.getRequest(target))
            out.flush()
            val input = socket.getInputStream()
            val buf = ByteArray(64 * 1024)
            var first = true
            val samples = SpeedMeter.sample(
                durationMs = durationMs,
                everyMs = RatePolicy.SAMPLE_EVERY_MS,
                nowMs = { System.nanoTime() / 1_000_000L },
                read = {
                    val n = input.read(buf)
                    if (first && n > 0) {
                        first = false
                        SimHttp.badStatus(buf, n)?.let { throw IOException("HTTP status: $it") }
                    }
                    n
                },
                keepGoing = { isActive },
            )
            CalibratorMath.pacerRate(samples, discardMs)
        }
    }
}
