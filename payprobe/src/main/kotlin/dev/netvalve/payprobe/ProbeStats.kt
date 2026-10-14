package dev.netvalve.payprobe

/**
 * Statistics over probe samples. Failures are INCLUDED in the percentiles (a timeout counts as
 * 5000 ms), because an app that waits five seconds for nothing has had a five-second delay.
 * Percentiles use the nearest-rank method.
 */
object ProbeStats {

    fun percentile(sortedAscending: List<Long>, p: Double): Long {
        if (sortedAscending.isEmpty()) return 0L
        val rank = Math.ceil(p / 100.0 * sortedAscending.size).toInt().coerceIn(1, sortedAscending.size)
        return sortedAscending[rank - 1]
    }

    fun summarize(samples: List<ProbeSample>): ProbeSummary {
        if (samples.isEmpty()) return ProbeSummary.EMPTY
        val sorted = samples.map { it.latencyMs }.sorted()
        return ProbeSummary(
            count = samples.size,
            failures = samples.count { !it.ok },
            p50 = percentile(sorted, 50.0),
            p95 = percentile(sorted, 95.0),
            p99 = percentile(sorted, 99.0),
            max = sorted.last(),
            mean = sorted.sum().toDouble() / sorted.size,
        )
    }
}
