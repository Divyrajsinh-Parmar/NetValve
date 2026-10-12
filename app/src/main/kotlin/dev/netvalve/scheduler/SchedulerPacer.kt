package dev.netvalve.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Direction lane. Each lane has its own pacer and its own rate (R_up and R_down differ). */
enum class Lane { UPLOAD, DOWNLOAD }

/**
 * Coroutine adapter around [PriorityPacerCore]: this is the piece the relay will call.
 *
 *     // before writing n bytes of a flow:
 *     schedulerPacer.acquire(tier, flowId, Lane.UPLOAD, n)   // suspends until allowed
 *
 * One pump coroutine per lane ticks every [SchedulerConfig.tickMs] while somebody waits and
 * sleeps (no wake-ups, no battery cost) while nobody does. The state machine itself is
 * guarded by a lock because [acquire] is called from many relay coroutines.
 *
 * Pure Kotlin plus kotlinx.coroutines: no Android imports.
 *
 * @param nowMs monotonic clock in milliseconds (on Android pass SystemClock.elapsedRealtime).
 */
class SchedulerPacer(
    private val scope: CoroutineScope,
    upload: SchedulerConfig,
    download: SchedulerConfig,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private inner class LaneState(config: SchedulerConfig) {
        val core = PriorityPacerCore<CompletableDeferred<Unit>>(config)
        val wake = Channel<Unit>(Channel.CONFLATED)

        fun anyWaiters(): Boolean = Tier.values().any { core.hasWaiters(it) }

        suspend fun pump() {
            while (true) {
                wake.receive() // sleep until someone submitted
                while (true) {
                    val grants = synchronized(core) { core.tick(nowMs()) }
                    for (g in grants) if (g.completed) g.token.complete(Unit)
                    if (synchronized(core) { !anyWaiters() }) break
                    delay(synchronized(core) { core.config.tickMs })
                }
            }
        }
    }

    private val up = LaneState(upload)
    private val down = LaneState(download)

    init {
        scope.launch { up.pump() }
        scope.launch { down.pump() }
    }

    private fun lane(l: Lane) = if (l == Lane.UPLOAD) up else down

    /**
     * Suspend until [bytes] bytes of [flowId] (belonging to [tier]) may be sent in [lane].
     * Never drops. If the calling coroutine is cancelled (connection closed) the pending request
     * is removed so it cannot consume budget.
     */
    suspend fun acquire(tier: Tier, flowId: Long, lane: Lane, bytes: Int) {
        if (bytes <= 0) return
        val l = lane(lane)
        val done = CompletableDeferred<Unit>()
        synchronized(l.core) { l.core.submit(tier, flowId, bytes, done, nowMs()) }
        l.wake.trySend(Unit)
        try {
            done.await()
        } catch (e: CancellationException) {
            synchronized(l.core) { l.core.cancelFlow(flowId) }
            throw e
        }
    }

    /** Forget everything a flow still has queued (call when its connection closes). */
    fun releaseFlow(flowId: Long) {
        for (l in listOf(up, down)) {
            val dropped = synchronized(l.core) { l.core.cancelFlow(flowId) }
            dropped.forEach { it.cancel() }
        }
    }

    /** Apply a new rate or parameters to one lane without reconnecting anything. */
    fun updateConfig(lane: Lane, config: SchedulerConfig) {
        val l = lane(lane)
        synchronized(l.core) { l.core.updateConfig(config) }
    }
}
