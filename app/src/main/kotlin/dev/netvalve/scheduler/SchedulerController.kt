package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The one object the relay talks to. It owns the [SchedulerPacer], follows the settings
 * (mode and rates) and resolves a UID to its tier.
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
    private val tiers: TierResolver,
    nowMs: () -> Long,
) {
    @Volatile
    var current: SchedulerSettings = SchedulerSettings()
        private set

    private val pacer = SchedulerPacer(scope, SchedulerConfig(), SchedulerConfig(), nowMs)
    private val nextFlowId = AtomicLong(1L)

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

    fun tierOf(uid: Int): Tier = tiers.tierOfUid(uid)

    /** Suspends until [bytes] of this flow may go out in [lane]; returns at once when inactive. */
    suspend fun acquire(uid: Int, flowId: Long, lane: Lane, bytes: Int) {
        if (!current.activeFor(lane)) return
        pacer.acquire(tiers.tierOfUid(uid), flowId, lane, bytes)
    }

    /** Forget whatever the flow still has queued (call when its connection ends). */
    fun releaseFlow(flowId: Long) = pacer.releaseFlow(flowId)

    /** One line for the debug log at flow start: tier, mode and R of both directions. */
    fun describeFlow(uid: Int, flowId: Long): String {
        val s = current
        return "sched flow=$flowId tier=${tiers.tierOfUid(uid)} mode=${s.mode} " +
            "R_down=${RateUnits.formatMbps(s.rateDownBytesPerSec)} Mbps R_up=${RateUnits.formatMbps(s.rateUpBytesPerSec)} Mbps"
    }
}
