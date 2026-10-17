package dev.netvalve.scheduler

import java.util.Locale

/**
 * What the relay needs to know about scheduling, independent of Android and of the persisted
 * `GlobalSettings`. The Android layer maps GlobalSettings to this class.
 *
 * Only [SchedMode.PRIORITY_PACER] (Mode B) is active on the device. [SchedMode.ADAPTIVE_THROTTLE]
 * (Mode A) stays a simulation result (roadmap M7), so it behaves like OFF here.
 *
 * A rate of 0 means "not set": that direction is not scheduled.
 */
data class SchedulerSettings(
    val mode: SchedMode = SchedMode.OFF,
    val rateUpBytesPerSec: Long = 0L,
    val rateDownBytesPerSec: Long = 0L,
) {
    fun rateFor(lane: Lane): Long = if (lane == Lane.UPLOAD) rateUpBytesPerSec else rateDownBytesPerSec

    fun activeFor(lane: Lane): Boolean = mode == SchedMode.PRIORITY_PACER && rateFor(lane) > 0L
}

/** Conversions between the Mbps the user types and the bytes per second the pacer uses. */
object RateUnits {
    /** 1 Mbps = 1,000,000 bit/s = 125,000 B/s. */
    fun mbpsToBytesPerSec(mbps: Double): Long =
        if (mbps.isNaN() || mbps <= 0.0) 0L else Math.round(mbps * 125_000.0)

    fun bytesPerSecToMbps(bytesPerSec: Long): Double = bytesPerSec / 125_000.0

    /**
     * Parses what a person types: "12", "12.5" or "12,5" (comma accepted for phone keyboards).
     * Returns null for text that is not a number, is negative, or is above [MAX_MBPS].
     */
    fun parseMbps(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite() || value < 0.0 || value > MAX_MBPS) return null
        return value
    }

    /** "0" for unset, otherwise up to two decimals without trailing zeros ("37.5", "40"). */
    fun formatMbps(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0"
        val s = String.format(Locale.ROOT, "%.2f", bytesPerSecToMbps(bytesPerSec))
        return s.trimEnd('0').trimEnd('.')
    }

    const val MAX_MBPS = 10_000.0

    /** "21.3 Mbps" from a rate in bytes per second (decimal megabits, the unit speed tests use). */
    fun mbpsText(bytesPerSec: Long): String =
        String.format(Locale.ROOT, "%.1f Mbps", bytesPerSec.coerceAtLeast(0L) * 8.0 / 1_000_000.0)

    /** Average speed of a run in Mbps: total bytes over the elapsed seconds (0 when no time has passed). */
    fun averageMbps(totalBytes: Long, seconds: Int): Double =
        if (seconds <= 0) 0.0 else totalBytes.coerceAtLeast(0L) * 8.0 / 1_000_000.0 / seconds

    /** The shared speed is set to this part of the measured link speed (roadmap: R = 0.9 x link speed). */
    const val SHARE_FRACTION = 0.9

    /**
     * From what a person typed as the measured speed ("25") to the text for the shared-speed box
     * ("22.5"): 90 % rounded to one decimal. Null when the text is not a positive number or the
     * result would round to 0.
     */
    fun shareTextOf(measuredText: String): String? {
        val measured = parseMbps(measuredText) ?: return null
        val share = Math.round(measured * SHARE_FRACTION * 10.0) / 10.0
        if (share <= 0.0) return null
        return formatMbps(mbpsToBytesPerSec(share))
    }
}
