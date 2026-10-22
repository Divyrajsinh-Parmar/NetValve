package dev.netvalve.demo

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Numbers about a series of probes. A probe that got no answer in time is a lost probe (null).
 * These are PROBE-BASED proxies: "ping" is the time of one small web request (connect and first answer
 * byte), "loss" is the share of probes without an answer and "jitter" is the average change between
 * neighbouring answered probes. They are not the game's own ping, FPS or real packet loss.
 */
data class LatencyStats(
    val sent: Int,
    val lost: Int,
    val medianMs: Long?,
    val p95Ms: Long?,
    val maxMs: Long?,
    val jitterMs: Double?,
) {
    val received: Int get() = sent - lost
    val lossPercent: Double get() = if (sent == 0) 0.0 else 100.0 * lost / sent
}

enum class LagScore { GOOD, OKAY, BAD, NO_DATA }

object LatencyMath {
    fun summarize(samples: List<Long?>): LatencyStats {
        val answered = samples.filterNotNull().map { it.coerceAtLeast(0L) }
        val sorted = answered.sorted()
        val n = sorted.size
        val median: Long? = when {
            n == 0 -> null
            n % 2 == 1 -> sorted[n / 2]
            else -> (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        }
        val p95: Long? = if (n == 0) null else sorted[ceil(0.95 * n).toInt().coerceIn(1, n) - 1]
        val jitter: Double? = if (n < 2) null else answered.zipWithNext { a, b -> abs(b - a).toDouble() }.average()
        return LatencyStats(
            sent = samples.size,
            lost = samples.size - n,
            medianMs = median,
            p95Ms = p95,
            maxMs = sorted.lastOrNull(),
            jitterMs = jitter,
        )
    }
}

/**
 * The lag score. The limits are OUR OWN choice (hypothesis, not a standard) and are printed in the report:
 * good = 95 percent of probes answered within [GOOD_P95_MS] and at most [GOOD_LOSS_PERCENT] percent lost;
 * okay = within [OKAY_P95_MS] and at most [OKAY_LOSS_PERCENT] percent lost; otherwise bad.
 * A probe is a whole small web request (connect + first byte), so it takes about two round trips: the
 * limits are higher than the usual limits for a game ping.
 */
object LagRules {
    const val MIN_PROBES = 5
    const val GOOD_P95_MS = 150L
    const val OKAY_P95_MS = 300L
    const val GOOD_LOSS_PERCENT = 1.0
    const val OKAY_LOSS_PERCENT = 3.0

    fun score(s: LatencyStats): LagScore {
        if (s.sent < MIN_PROBES) return LagScore.NO_DATA
        val p95 = s.p95Ms ?: return LagScore.BAD
        return when {
            p95 <= GOOD_P95_MS && s.lossPercent <= GOOD_LOSS_PERCENT -> LagScore.GOOD
            p95 <= OKAY_P95_MS && s.lossPercent <= OKAY_LOSS_PERCENT -> LagScore.OKAY
            else -> LagScore.BAD
        }
    }
}
