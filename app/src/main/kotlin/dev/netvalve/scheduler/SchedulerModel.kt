package dev.netvalve.scheduler

/**
 * Priority tier of an app. Lower [level] means more important.
 *
 * Pure Kotlin on purpose: nothing in this package imports Android, so every
 * class here can be unit-tested on a plain JVM in milliseconds.
 */
enum class Tier(val level: Int) {
    HIGH(0),
    MEDIUM(1),
    LOW(2);

    companion object {
        /** The High tier is deliberately small (roadmap: at most 3 apps). */
        const val MAX_HIGH_APPS = 3

        /** Unknown or unattributed levels are treated as [MEDIUM]. */
        fun fromLevel(level: Int): Tier = values().firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class SchedMode {
    /** No scheduling: behave exactly like stock NetValve (static caps only). */
    OFF,

    /** Mode A: when a High app is active, cap Medium and Low. Reactive. */
    ADAPTIVE_THROTTLE,

    /** Mode B: shared pacer with strict priority, floors and caps. Work-conserving. */
    PRIORITY_PACER,
}

/**
 * Parameters of the scheduler for ONE direction (create one config per direction).
 *
 * @property rateBytesPerSec pacer rate R. Normally 0.9 x measured goodput
 *           (see [CalibratorMath]) or a fixed demo value.
 * @property holdMs how long a tier-High app counts as "active" after its last byte.
 * @property alphaMedium share of R that Medium may use while High is active.
 * @property alphaLow share of R that Low may use while High is active.
 * @property floorMedium share of R that Medium is always guaranteed (no starvation).
 * @property floorLow share of R that Low is always guaranteed (no starvation).
 * @property tickMs scheduling tick length.
 * @property grantBytes maximum bytes handed out to one flow per turn. Small grants
 *           keep head-of-line blocking low: 4 KB at 5 Mbps is about 6.5 ms.
 */
data class SchedulerConfig(
    val mode: SchedMode = SchedMode.OFF,
    val rateBytesPerSec: Long = 625_000L, // 5 Mbps
    val holdMs: Long = 2_000L,
    val alphaMedium: Double = 0.50,
    val alphaLow: Double = 0.20,
    val floorMedium: Double = 0.10,
    val floorLow: Double = 0.05,
    val tickMs: Long = 10L,
    val grantBytes: Int = 4096,
) {
    init {
        require(rateBytesPerSec > 0) { "rateBytesPerSec must be > 0" }
        require(holdMs >= 0) { "holdMs must be >= 0" }
        require(tickMs > 0) { "tickMs must be > 0" }
        require(grantBytes > 0) { "grantBytes must be > 0" }
        require(alphaMedium in 0.0..1.0 && alphaLow in 0.0..1.0) { "alpha values must be in 0..1" }
        require(floorMedium in 0.0..0.5 && floorLow in 0.0..0.5) { "floors must be in 0..0.5" }
        require(floorMedium + floorLow < 1.0) { "floors must leave room for High" }
    }
}
