package dev.netvalve.health

import dev.netvalve.log.LogCategory
import dev.netvalve.log.LogLevel
import dev.netvalve.scheduler.RateUnits
import dev.netvalve.utils.Format
import java.util.Locale

/** The easy status shown on the Stats tab. */
enum class HealthLevel { OFF, PAUSED, NO_PRIORITY_APPS, NO_SPEED, CHECK, WAITING, GOOD }

data class HealthVerdict(val level: HealthLevel, val title: String, val detail: String)

/** What kind of problem a log line reports (see [HealthRules.classify]). */
enum class FailureKind { ESTABLISH, ENGINE, AUTO_SPEED, CONNECT }

/**
 * The rules behind the Stats tab. Pure Kotlin, tested on a JVM.
 *
 * The thresholds are CHOSEN by us (hypotheses, not measured facts); they are written down here and
 * shown in the report so a reader can judge them:
 *  - a real failure in the last hour turns the status to "check";
 *  - the priority apps' average wait in the scheduler above [PRIORITY_WAIT_OK_MS] ms turns it to "check";
 *  - fewer than [MIN_PRIORITY_CHUNKS] waiting-time samples of priority apps = "not enough data yet".
 */
object HealthRules {
    const val RECENT_FAILURE_MS = 60L * 60_000L
    const val PRIORITY_WAIT_OK_MS = 50.0
    const val MIN_PRIORITY_CHUNKS = 50L

    /**
     * Which problem (if any) a log line is. The log lines are the only source, so the counters
     * need no change in the relay or the VPN service. If the user raises the minimum log level in
     * the Logs tab, WARNING lines are no longer written and not counted (limit, stated in the report).
     */
    fun classify(level: LogLevel, category: LogCategory, message: String): FailureKind? {
        if (level.ordinal < LogLevel.WARNING.ordinal) {
            // INFO line of the automatic speed: "... measuring failed, kept the last known value" / "no known value".
            return if (message.startsWith("auto speed:") && message.contains("measuring failed,")) FailureKind.AUTO_SPEED else null
        }
        return when {
            message.startsWith("connect failed") -> FailureKind.CONNECT
            message.startsWith("establish failed") -> FailureKind.ESTABLISH
            message.startsWith("auto speed:") && (message.contains("measuring failed,") || message.contains("stopped by an error")) ->
                FailureKind.AUTO_SPEED
            level == LogLevel.ERROR && category == LogCategory.ERROR -> FailureKind.ENGINE
            else -> null
        }
    }

    fun evaluate(
        running: Boolean,
        paused: Boolean,
        priorityApps: Int,
        rateDownBytesPerSec: Long,
        c: HealthCounters,
        nowMs: Long,
    ): HealthVerdict {
        if (!running) return HealthVerdict(HealthLevel.OFF, "Protection is off", "Switch it on in Home to protect your priority apps.")
        if (paused) return HealthVerdict(HealthLevel.PAUSED, "Paused", "Everything goes through without priority until you resume.")
        if (priorityApps <= 0) {
            return HealthVerdict(HealthLevel.NO_PRIORITY_APPS, "No priority apps chosen", "Pick your priority apps in the Apps tab.")
        }
        if (rateDownBytesPerSec <= 0L) {
            return HealthVerdict(
                HealthLevel.NO_SPEED,
                "Check: speed not measured yet",
                "Priority works only after the app has measured your internet speed. It tries again by itself.",
            )
        }
        if (c.lastFailureAtMs > 0L && nowMs - c.lastFailureAtMs in 0..RECENT_FAILURE_MS) {
            return HealthVerdict(HealthLevel.CHECK, "Check: a problem happened in the last hour", "See the problem counts below.")
        }
        val avg = c.priorityAvgWaitMs()
        if (c.priorityWaitChunks() < MIN_PRIORITY_CHUNKS || avg == null) {
            return HealthVerdict(HealthLevel.WAITING, "Working, not enough data yet", "Use a priority app for a while; the numbers fill in.")
        }
        if (avg > PRIORITY_WAIT_OK_MS) {
            return HealthVerdict(
                HealthLevel.CHECK,
                "Check: priority apps waited long",
                "On average ${ms(avg)} ms per chunk (limit we chose: ${PRIORITY_WAIT_OK_MS.toInt()} ms).",
            )
        }
        return HealthVerdict(HealthLevel.GOOD, "Your priority apps are getting their data well", "Average wait of priority apps: ${ms(avg)} ms.")
    }

    private fun ms(v: Double): String = String.format(Locale.US, "%.1f", v)
}

/** A plain-text copy of the summary (for the Share button and the report). Pure Kotlin. */
object HealthReport {
    fun text(c: HealthCounters, verdict: HealthVerdict, speeds: List<Pair<String, Long?>>, nowMs: Long): String = buildString {
        fun pct(v: Double?) = if (v == null) "no data" else String.format(Locale.US, "%.1f %%", v)
        fun waitMs(v: Double?) = if (v == null) "no data" else String.format(Locale.US, "%.1f ms", v)
        val all = c.allBytes()
        val priorityShare = if (all > 0L) 100.0 * c.priorityBytes() / all else null
        append("NetValve health summary\n")
        append("Status: ").append(verdict.title).append(" - ").append(verdict.detail).append('\n')
        append("Counting since: ").append(if (c.sinceMs > 0L) java.time.Instant.ofEpochMilli(c.sinceMs).toString() else "no data").append('\n')
        append("Report time: ").append(java.time.Instant.ofEpochMilli(nowMs)).append('\n')
        append("Total running time (tunnel up, ON or Paused): ").append(Format.duration(c.runningMs)).append('\n')
        append("Longest single run: ").append(Format.duration(c.longestRunMs)).append('\n')
        append("Tunnel starts: ").append(c.tunnelStarts).append("; app starts: ").append(c.appStarts).append('\n')
        append("Data through the tunnel: ").append(Format.bytes(all)).append(" (priority apps ")
            .append(Format.bytes(c.priorityBytes())).append(", normal apps ").append(Format.bytes(c.normalBytes()))
            .append(", PayProbe ").append(Format.bytes(c.probeBytes())).append(")\n")
        append("Priority apps' share of all bytes: ").append(pct(priorityShare)).append('\n')
        append("Average wait in the scheduler: priority apps ").append(waitMs(c.priorityAvgWaitMs()))
            .append(", normal apps ").append(waitMs(c.normalAvgWaitMs())).append('\n')
        append("Longest single wait: priority apps ").append(c.priorityMaxWaitMs()).append(" ms, normal apps ")
            .append(c.normalMaxWaitMs()).append(" ms\n")
        append("Bytes attributed to a recognised app: ").append(pct(c.attributedPercent())).append('\n')
        append("Problems: setup failures ").append(c.establishFailures).append(", engine errors ").append(c.engineErrors)
            .append(", speed measurement failures ").append(c.autoSpeedFailures).append('\n')
        append("Connections of apps that could not be opened (not a scheduler fault): ").append(c.connectFailures).append('\n')
        for ((name, bytesPerSec) in speeds) {
            append("Last measured shared speed ").append(name).append(": ")
                .append(if (bytesPerSec == null || bytesPerSec <= 0L) "none" else RateUnits.formatMbps(bytesPerSec) + " Mbps").append('\n')
        }
        append("How it is computed: bytes and waits are counted per chunk in the relay (scheduler.acquire); the wait is counted only ")
            .append("while the scheduler is active (a shared speed is set). Thresholds for \"check\" (a problem in the last hour, ")
            .append("average priority wait above ").append(HealthRules.PRIORITY_WAIT_OK_MS.toInt()).append(" ms) are our own choice. ")
            .append("Problems are counted from log lines, so they need the log level WARNING or lower. This is not a measurement of game FPS or real packet loss.\n")
    }
}
