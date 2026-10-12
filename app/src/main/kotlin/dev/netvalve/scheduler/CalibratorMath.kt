package dev.netvalve.scheduler

/**
 * The maths of calibration, separated from the network code that produces the samples.
 *
 * Idea (same as router SQM): shape slightly BELOW the real capacity so the queue forms
 * inside our pacer, where we control the order. R = 0.9 x steady-state goodput.
 */
object CalibratorMath {
    const val SAFETY_FACTOR = 0.9
    const val DEFAULT_DISCARD_MS = 2_000L

    /** [cumulativeBytes] is the total number of bytes received or sent at [tMs] (milliseconds). */
    data class Sample(val tMs: Long, val cumulativeBytes: Long)

    /** Average goodput in bytes/s after discarding the TCP slow-start window; null if unusable. */
    fun steadyRate(samples: List<Sample>, discardMs: Long = DEFAULT_DISCARD_MS): Long? {
        if (samples.size < 2) return null
        val start = samples.first().tMs + discardMs
        val steady = samples.filter { it.tMs >= start }
        if (steady.size < 2) return null
        val dtMs = steady.last().tMs - steady.first().tMs
        val bytes = steady.last().cumulativeBytes - steady.first().cumulativeBytes
        if (dtMs <= 0 || bytes <= 0) return null
        return (bytes * 1000.0 / dtMs).toLong()
    }

    /** The pacer rate R for one direction, or null if the measurement is unusable. */
    fun pacerRate(
        samples: List<Sample>,
        discardMs: Long = DEFAULT_DISCARD_MS,
        factor: Double = SAFETY_FACTOR,
    ): Long? = steadyRate(samples, discardMs)?.let { (it * factor).toLong().coerceAtLeast(1L) }
}
