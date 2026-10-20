package dev.netvalve.calibration

import dev.netvalve.data.model.NetworkType

/**
 * The rules of the automatic shared speed (R). Pure Kotlin (no Android) so they are tested on a JVM.
 *
 * Safety rule behind everything here: a failed or silly measurement must never slow the internet
 * down. [choose] therefore only ever returns a fresh measurement, the last known value of the same
 * network type, or 0 (= "not scheduled", no shaping).
 */
object RatePolicy {

    /** Download test: how long it runs and how much of the start is thrown away (TCP slow start). */
    const val MEASURE_MS = 8_000L
    const val DISCARD_MS = 2_000L
    const val SAMPLE_EVERY_MS = 250L

    /** A result below this (0.8 Mbit/s) is treated as a failed measurement, not as the real speed. */
    const val MIN_USABLE_BYTES_PER_SEC = 100_000L

    /** Wait this long after a network change before measuring, so the new network can settle. */
    const val CHANGE_DEBOUNCE_MS = 3_000L

    /** While protection stays on, measure again this often (the link speed moves during the day). */
    const val REMEASURE_INTERVAL_MS = 20L * 60_000L

    /** The periodic measurement waits for a quiet moment; it asks again after this long. */
    const val BUSY_RETRY_MS = 60_000L

    /** "Quiet" = the tunnel carries less than this (bytes/s, 1.2 Mbit/s) right now. */
    const val QUIET_DOWNLOAD_BYTES_PER_SEC = 150_000L

    /** The test file (served over plain HTTP; the test uses its own socket, not the system HTTP stack). */
    const val DOWNLOAD_URL = "http://speedtest.mumbai1.linode.com/100MB-mumbai.bin"

    /**
     * The rate to apply: the fresh [measured] value when it is usable, otherwise the [known] value
     * stored for this network type, otherwise 0 (no shaping).
     */
    fun choose(measured: Long?, known: Long?): Long {
        if (measured != null && measured >= MIN_USABLE_BYTES_PER_SEC) return measured
        if (known != null && known >= MIN_USABLE_BYTES_PER_SEC) return known
        return 0L
    }

    fun isUsable(measured: Long?): Boolean = measured != null && measured >= MIN_USABLE_BYTES_PER_SEC

    fun isQuiet(liveDownloadBytesPerSec: Long): Boolean =
        liveDownloadBytesPerSec < QUIET_DOWNLOAD_BYTES_PER_SEC

    /** The key under which the rate of a network type is stored, or null when it is not measured. */
    fun networkKey(type: NetworkType): String? = when (type) {
        NetworkType.WIFI -> "WIFI"
        NetworkType.MOBILE -> "MOBILE"
        NetworkType.ETHERNET -> "ETHERNET"
        else -> null
    }
}
