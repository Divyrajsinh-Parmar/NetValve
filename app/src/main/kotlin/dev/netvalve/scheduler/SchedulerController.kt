package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The one object the relay talks to. It owns the [RankedPacer], follows the settings
 * (mode and rates) and resolves a UID to its priority level (the user's ordered priority list,
 * see [PriorityOrder]; every app outside the list is "normal").
 *
 * Relay usage, per chunk of n bytes (see `FlowSupervisor`):
 *
 *     scheduler.acquire(uid, flowId, Lane.UPLOAD, n)   // returns at once when the scheduler is off
 *     throttleManager.pace(bucket, n)                  // existing static cap still applies on top
 *
 * Mode OFF, or a direction whose rate is 0, behaves exactly like stock NetValve: [acquire]
 * returns immediately and nothing is queued.
 *
 * Pure Kotlin plus coroutines: no Android imports.
 */
class SchedulerController(
    scope: CoroutineScope,
    settings: Flow<SchedulerSettings>,
    private val levels: LevelResolver,
    private val nowMs: () -> Long,
) {
    @Volatile
    var current: SchedulerSettings = SchedulerSettings()
        private set

    private val pacer = RankedPacer(scope, SchedulerConfig(), SchedulerConfig(), PriorityLevels.COUNT, nowMs)
    private val nextFlowId = AtomicLong(1L)
    private val counter = LevelByteCounter(PriorityLevels.COUNT)
    /** Flows whose level is set by hand (live demo only); empty in normal use. */
    private val flowLevelOverride = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    init {
        scope.launch { settings.collect { apply(it) } }
    }

    private fun apply(s: SchedulerSettings) {
        for (lane in Lane.values()) {
            val rate = s.rateFor(lane)
            if (rate > 0L) {
                pacer.updateConfig(lane, SchedulerConfig(mode = SchedMode.PRIORITY_PACER, rateBytesPerSec = rate))
            }
        }
        current = s
    }

    fun isActive(lane: Lane): Boolean = current.activeFor(lane)

    fun newFlowId(): Long = nextFlowId.getAndIncrement()

    fun levelOf(uid: Int): Int = levels.levelOfUid(uid)

    /**
     * Suspends until [bytes] of this flow may go out in [lane]; returns at once when inactive.
     * Every chunk is also counted per level (bytes, and the wait while scheduled) for the health
     * summary; the counting is a few atomic adds and cannot fail or block.
     */
    suspend fun acquire(uid: Int, flowId: Long, lane: Lane, bytes: Int) {
        // Looked up even when the scheduler is off (for the counters); a failure here must not stop the flow.
        val level = flowLevelOverride[flowId] ?: runCatching { levels.levelOfUid(uid) }.getOrDefault(PriorityLevels.NORMAL)
        counter.addBytes(level, lane, bytes, attributed = uid >= 0)
        if (!current.activeFor(lane)) return
        val startedAt = nowMs()
        pacer.acquire(level, flowId, lane, bytes)
        counter.addWait(level, nowMs() - startedAt)
    }

    /** What the relay carried per level since the app process started (patch B3). */
    fun totals(): LevelTotals = counter.snapshot()

    /** Forget whatever the flow still has queued (call when its connection ends). */
    fun releaseFlow(flowId: Long) {
        flowLevelOverride.remove(flowId)
        pacer.releaseFlow(flowId)
    }

    /** Puts one flow on [level] whatever its app is (live demo, patch E1). */
    fun overrideFlowLevel(flowId: Long, level: Int) {
        if (flowId != 0L) flowLevelOverride[flowId] = level
    }

    /** One line for the debug log at flow start: level, mode and R of both directions. */
    fun describeFlow(uid: Int, flowId: Long): String {
        val s = current
        return "sched flow=$flowId level=${levels.levelOfUid(uid)} mode=${s.mode} " +
            "R_down=${RateUnits.formatMbps(s.rateDownBytesPerSec)} Mbps R_up=${RateUnits.formatMbps(s.rateUpBytesPerSec)} Mbps"
    }
}
