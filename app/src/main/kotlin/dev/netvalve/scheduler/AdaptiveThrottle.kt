package dev.netvalve.scheduler

/**
 * Mode A: the simple, reactive approach.
 *
 * While a High app moved bytes within the last [SchedulerConfig.holdMs], Medium and Low
 * get a rate cap (alpha x R); otherwise nobody is capped. The hold time prevents the cap
 * from flapping on and off. Weakness (documented, measured in the simulator): the first
 * packets after an idle period meet whatever queue Low built while it was uncapped.
 */
class AdaptiveThrottle(private var config: SchedulerConfig) {

    private var lastHighMs = NEVER

    fun updateConfig(newConfig: SchedulerConfig) {
        config = newConfig
    }

    /** Call whenever the High tier moved bytes (either direction). */
    fun onHighBytes(nowMs: Long) {
        lastHighMs = nowMs
    }

    fun isHighActive(nowMs: Long): Boolean = nowMs - lastHighMs < config.holdMs

    /** Rate cap in bytes per second for [tier] right now, or null when it is unlimited. */
    fun capBytesPerSec(tier: Tier, nowMs: Long): Long? = when {
        tier == Tier.HIGH -> null
        !isHighActive(nowMs) -> null
        tier == Tier.MEDIUM -> (config.alphaMedium * config.rateBytesPerSec).toLong()
        else -> (config.alphaLow * config.rateBytesPerSec).toLong()
    }

    private companion object {
        const val NEVER = -1_000_000_000_000L
    }
}
