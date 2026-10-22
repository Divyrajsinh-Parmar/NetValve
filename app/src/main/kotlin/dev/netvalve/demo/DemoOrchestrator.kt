package dev.netvalve.demo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

enum class DemoPhase { IDLE, BEFORE, AFTER }

enum class DemoStep { PREPARING, IDLE, BEFORE, AFTER, RESTORING, DONE, FAILED }

/** What the orchestrator needs from the tunnel. The real one restarts and pauses the VPN service. */
interface DemoControls {
    /** Puts the app's own traffic into the tunnel (a tunnel restart). False = it did not work. */
    suspend fun enter(): Boolean
    suspend fun pause(): Boolean
    suspend fun resume(): Boolean

    /** Puts everything back (leaves the demo mode, restarts the tunnel). Must be safe to call at any time. */
    suspend fun leave()
}

/** The big background download (the "other app"). */
interface LoadGenerator {
    fun start()
    fun stop()
    fun totalBytes(): Long

    /** Why the download may not have worked (error count and last error), or null when nothing went wrong. */
    fun problem(): String? = null
}

/** One small request; the time in ms, or null when there was no answer in time. */
interface Prober {
    suspend fun probeOnce(): Long?
}

data class DemoTimings(
    val idleMs: Long = 6_000L,
    val loadedMs: Long = 18_000L,
    /** The start of every loaded phase is not counted (the line fills up, the pacer settles). */
    val warmupMs: Long = 3_000L,
    val probeEveryMs: Long = 500L,
)

data class PhaseResult(
    val phase: DemoPhase,
    val samples: List<Long?>,
    val stats: LatencyStats,
    val score: LagScore,
    /** Speed of the background download in this phase (bytes/s), 0 without load. */
    val loadBytesPerSec: Long,
    /** Errors of the background download in this phase, or null. */
    val loadProblem: String? = null,
) {
    /** A loaded phase counts only when the download really ran (see [DemoRules.MIN_LOAD_BYTES_PER_SEC]). */
    val valid: Boolean get() = phase == DemoPhase.IDLE || loadBytesPerSec >= DemoRules.MIN_LOAD_BYTES_PER_SEC
}

object DemoRules {
    /** Below 1 Mbps (our choice) the "big download" did not run, so BEFORE and AFTER cannot be compared. */
    const val MIN_LOAD_BYTES_PER_SEC = 125_000L
}

data class DemoProgress(
    val step: DemoStep,
    val msLeft: Long = 0L,
    val liveMs: Long? = null,
    val phaseSamples: List<Long?> = emptyList(),
    val results: List<PhaseResult> = emptyList(),
    val message: String? = null,
) {
    /** True when every loaded phase had a running download. */
    val valid: Boolean get() = results.all { it.valid }
}

/**
 * The live demo: 1. a quiet line (probes only), 2. BEFORE: priority paused (the tunnel and its relay stay on; new flows pass unshaped), big download plus probes,
 * 3. AFTER: priority on, big download plus probes. Whatever happens (a failure, a cancel), the
 * download is stopped and [DemoControls.leave] is called, so the phone is put back.
 *
 * Pure Kotlin plus coroutines: tested on a JVM with fakes.
 */
class DemoOrchestrator(
    private val controls: DemoControls,
    private val load: LoadGenerator,
    private val prober: Prober,
    private val timings: DemoTimings = DemoTimings(),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(onProgress: (DemoProgress) -> Unit): DemoProgress {
        val results = ArrayList<PhaseResult>()
        var outcome: DemoProgress
        try {
            outcome = execute(results, onProgress)
        } catch (e: CancellationException) {
            throw e.also { restore(onProgress, results) }
        } catch (t: Throwable) {
            outcome = DemoProgress(DemoStep.FAILED, results = results.toList(), message = "${t.javaClass.simpleName}: ${t.message}")
        }
        restore(onProgress, results)
        onProgress(outcome)
        return outcome
    }

    private suspend fun restore(onProgress: (DemoProgress) -> Unit, results: List<PhaseResult>) {
        withContext(NonCancellable) {
            runCatching { load.stop() }
            runCatching { onProgress(DemoProgress(DemoStep.RESTORING, results = results.toList())) }
            runCatching { controls.leave() }
        }
    }

    private suspend fun execute(results: MutableList<PhaseResult>, onProgress: (DemoProgress) -> Unit): DemoProgress {
        fun failed(msg: String) = DemoProgress(DemoStep.FAILED, results = results.toList(), message = msg)

        onProgress(DemoProgress(DemoStep.PREPARING, results = results.toList()))
        if (!controls.enter()) return failed("The protection could not be restarted for the demo.")

        results += runPhase(DemoPhase.IDLE, DemoStep.IDLE, timings.idleMs, 0L, false, results, onProgress)

        if (!controls.pause()) return failed("The protection could not be paused.")
        results += runPhase(DemoPhase.BEFORE, DemoStep.BEFORE, timings.loadedMs, timings.warmupMs, true, results, onProgress)

        if (!controls.resume()) return failed("The protection could not be switched back on.")
        results += runPhase(DemoPhase.AFTER, DemoStep.AFTER, timings.loadedMs, timings.warmupMs, true, results, onProgress)

        return DemoProgress(DemoStep.DONE, results = results.toList())
    }

    private suspend fun runPhase(
        phase: DemoPhase,
        step: DemoStep,
        durationMs: Long,
        warmupMs: Long,
        withLoad: Boolean,
        done: List<PhaseResult>,
        onProgress: (DemoProgress) -> Unit,
    ): PhaseResult {
        if (withLoad) load.start()
        val start = nowMs()
        val counted = ArrayList<Long?>()
        var bytesAtWarm = -1L
        var warmAt = 0L
        while (nowMs() - start < durationMs) {
            val t0 = nowMs()
            val answer = prober.probeOnce()
            val now = nowMs()
            if (now - start >= warmupMs) {
                if (bytesAtWarm < 0L) {
                    bytesAtWarm = load.totalBytes()
                    warmAt = now
                }
                counted.add(answer)
            }
            onProgress(
                DemoProgress(
                    step = step,
                    msLeft = (durationMs - (now - start)).coerceAtLeast(0L),
                    liveMs = answer,
                    phaseSamples = counted.toList(),
                    results = done.toList(),
                ),
            )
            val wait = timings.probeEveryMs - (nowMs() - t0)
            if (wait > 0L) pause(wait)
        }
        val end = nowMs()
        val bytesEnd = if (withLoad) load.totalBytes() else 0L
        val problem = if (withLoad) runCatching { load.problem() }.getOrNull() else null
        if (withLoad) load.stop()
        val speed = if (withLoad && bytesAtWarm >= 0L && end > warmAt) {
            ((bytesEnd - bytesAtWarm) * 1000L / (end - warmAt)).coerceAtLeast(0L)
        } else {
            0L
        }
        val stats = LatencyMath.summarize(counted)
        return PhaseResult(phase, counted.toList(), stats, LagRules.score(stats), speed, problem)
    }
}

/** A plain-text copy of the demo result (Share button, report). Pure Kotlin. */
object DemoReport {
    fun text(results: List<PhaseResult>): String = buildString {
        fun ms(v: Long?) = if (v == null) "no data" else "$v ms"
        fun jit(v: Double?) = if (v == null) "no data" else String.format(Locale.US, "%.0f ms", v)
        append("NetValve live demo\n")
        if (results.any { !it.valid }) {
            append("RESULT NOT VALID: the big download did not run in at least one loaded phase (below 1 Mbps), so BEFORE and AFTER cannot be compared.\n")
        }
        for (r in results) {
            val name = when (r.phase) {
                DemoPhase.IDLE -> "Quiet line (no download, only probes)"
                DemoPhase.BEFORE -> "BEFORE (priority paused, tunnel still on, big download running)"
                DemoPhase.AFTER -> "AFTER (priority on, big download running)"
            }
            append(name).append('\n')
            append("  lag score: ").append(r.score.name).append("; probes ").append(r.stats.sent)
                .append(", lost ").append(r.stats.lost).append(String.format(Locale.US, " (%.1f %%)", r.stats.lossPercent)).append('\n')
            append("  ping (request time): median ").append(ms(r.stats.medianMs)).append(", 95 percent within ")
                .append(ms(r.stats.p95Ms)).append(", worst ").append(ms(r.stats.maxMs)).append(", jitter ").append(jit(r.stats.jitterMs)).append('\n')
            if (r.phase != DemoPhase.IDLE) {
                append("  speed of the big download: ").append(String.format(Locale.US, "%.1f Mbps", r.loadBytesPerSec * 8 / 1_000_000.0))
                    .append(if (r.valid) "" else " (NOT VALID)").append('\n')
                r.loadProblem?.let { append("  download problem: ").append(it).append('\n') }
            }
        }
        append("How it is measured: every 0.5 s a small web request (connect and first answer byte) goes to a fixed server; the first ")
            .append("3 s of a loaded phase are not counted. Ping, loss and jitter are probe-based proxies, not the game's own ping, FPS or real ")
            .append("packet loss. The lag score limits (95 percent of probes within ").append(LagRules.GOOD_P95_MS).append(" ms and at most ")
            .append(LagRules.GOOD_LOSS_PERCENT.toInt()).append(" percent lost = good; ").append(LagRules.OKAY_P95_MS).append(" ms and ")
            .append(LagRules.OKAY_LOSS_PERCENT.toInt()).append(" percent = okay) are our own choice. One run on one network is one sample: on some ")
            .append("networks there is no queue and the difference is small.\n")
    }
}
