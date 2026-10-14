package dev.netvalve.payprobe

/** One measurement: a fresh connection, one HTTPS GET, the full time it took. */
data class ProbeSample(
    val index: Int,
    val wallClockMs: Long,   // epoch milliseconds when the probe started
    val elapsedMs: Long,     // milliseconds since the run started
    val latencyMs: Long,     // full transaction time, capped at the timeout (a failure is stored as the cap)
    val ok: Boolean,
    val httpCode: Int,       // -1 when there was no HTTP response
    val note: String,        // exception name or "timeout"; empty on success
)

/** Result of one probe before it gets an index and timestamps. */
data class ProbeResult(
    val latencyMs: Long,
    val ok: Boolean,
    val httpCode: Int,
    val note: String,
)

data class ProbeSummary(
    val count: Int,
    val failures: Int,
    val p50: Long,
    val p95: Long,
    val p99: Long,
    val max: Long,
    val mean: Double,
) {
    companion object {
        val EMPTY = ProbeSummary(0, 0, 0, 0, 0, 0, 0.0)
    }
}
