package dev.netvalve.calibration

import dev.netvalve.scheduler.CalibratorMath

/** Turns a blocking byte source into the cumulative samples that [CalibratorMath] needs. Pure Kotlin. */
object SpeedMeter {

    /**
     * Reads until [durationMs] has passed, the stream ends, or [keepGoing] says stop.
     *
     * @param read blocks until it has bytes and returns how many it got, or -1 at the end of the stream.
     * @param nowMs a monotonic clock in milliseconds.
     * @return cumulative samples (time since the start, total bytes), at most one per [everyMs], plus
     *         the first and the last point.
     */
    fun sample(
        durationMs: Long,
        everyMs: Long,
        nowMs: () -> Long,
        read: () -> Int,
        keepGoing: () -> Boolean = { true },
    ): List<CalibratorMath.Sample> {
        val start = nowMs()
        var total = 0L
        val out = ArrayList<CalibratorMath.Sample>()
        out.add(CalibratorMath.Sample(0L, 0L))
        while (keepGoing()) {
            val n = read()
            val t = nowMs() - start
            if (n < 0) {
                out.add(CalibratorMath.Sample(t, total))
                return out
            }
            total += n
            if (t >= durationMs) {
                out.add(CalibratorMath.Sample(t, total))
                return out
            }
            if (t - out.last().tMs >= everyMs) out.add(CalibratorMath.Sample(t, total))
        }
        return out
    }
}
