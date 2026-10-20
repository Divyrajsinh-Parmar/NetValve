package dev.netvalve.health

import android.os.SystemClock
import dev.netvalve.di.AppScope
import dev.netvalve.log.LogEvent
import dev.netvalve.log.LogCategory
import dev.netvalve.log.Logger
import dev.netvalve.scheduler.LevelTotals
import dev.netvalve.scheduler.SchedulerController
import dev.netvalve.service.TunnelState
import dev.netvalve.service.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the long-term health numbers (patch B3). It only READS: the tunnel state, the scheduler's
 * counters and the log lines. It never sits in the packet path, so a failure here (storage, a bug)
 * can only lose numbers, never block or slow the internet: every step runs in its own try/catch.
 *
 *  - every 30 s while the tunnel is up: adds the running time; always: adds what the relay carried
 *    since the last step; then saves (also at every tunnel state change);
 *  - problems are counted from the log lines (see [HealthRules.classify]).
 */
@Singleton
class HealthRecorder @Inject constructor(
    private val controller: VpnController,
    private val scheduler: SchedulerController,
    private val repository: HealthRepository,
    private val logger: Logger,
    @AppScope private val scope: CoroutineScope,
) {
    private val _counters = MutableStateFlow(HealthCounters())

    /** The saved numbers plus what happened since the last save. */
    val counters: StateFlow<HealthCounters> = _counters.asStateFlow()

    private val lock = Mutex()
    private var runActive = false
    private var runStartMs = 0L
    private var lastTickMs = 0L
    private var previousTotals: LevelTotals = scheduler.totals()
    @Volatile private var dirty = false

    /** Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            try {
                val loaded = repository.load()
                _counters.value = loaded.copy(
                    sinceMs = if (loaded.sinceMs > 0L) loaded.sinceMs else System.currentTimeMillis(),
                    appStarts = loaded.appStarts + 1,
                )
                dirty = true
                flush()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.w(LogCategory.SYSTEM, "health: could not load the saved numbers (${t.javaClass.simpleName}); counting from zero")
            }
            launch { guarded("tunnel") { trackTunnel() } }
            launch { guarded("ticker") { tickLoop() } }
            launch { guarded("problems") { trackProblems() } }
        }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.w(LogCategory.SYSTEM, "health: $what counter stopped (${t.javaClass.simpleName}: ${t.message})")
        }
    }

    private suspend fun trackTunnel() {
        controller.status.map { it.state }.distinctUntilChanged().collect { state ->
            val active = state == TunnelState.RUNNING || state == TunnelState.PAUSED
            lock.withLock {
                val now = SystemClock.elapsedRealtime()
                if (active && !runActive) {
                    runActive = true
                    runStartMs = now
                    lastTickMs = now
                    mutate { it.copy(tunnelStarts = it.tunnelStarts + 1) }
                } else if (!active && runActive) {
                    tick(now)
                    runActive = false
                }
                flush()
            }
        }
    }

    private suspend fun tickLoop() {
        while (true) {
            delay(TICK_MS)
            lock.withLock {
                tick(SystemClock.elapsedRealtime())
                flush()
            }
        }
    }

    /** Must be called with [lock] held. */
    private fun tick(now: Long) {
        if (runActive) {
            val dt = (now - lastTickMs).coerceAtLeast(0L)
            lastTickMs = now
            val thisRun = now - runStartMs
            mutate { it.copy(runningMs = it.runningMs + dt, longestRunMs = maxOf(it.longestRunMs, thisRun)) }
        }
        val totals = scheduler.totals()
        val delta = totals.deltaFrom(previousTotals)
        previousTotals = totals
        mutate { it.plusLevels(delta) }
    }

    private suspend fun trackProblems() {
        var last: LogEvent? = null
        logger.events.collect { list ->
            val first = when {
                last == null -> list.size // lines written before the first look are not counted
                else -> list.indexOfLast { it === last }.let { if (it >= 0) it + 1 else 0 }
            }
            var changed = false
            for (i in first until list.size) {
                val e = list[i]
                val kind = HealthRules.classify(e.level, e.category, e.message) ?: continue
                changed = true
                mutate { c ->
                    when (kind) {
                        FailureKind.ESTABLISH -> c.copy(establishFailures = c.establishFailures + 1, lastFailureAtMs = System.currentTimeMillis())
                        FailureKind.ENGINE -> c.copy(engineErrors = c.engineErrors + 1, lastFailureAtMs = System.currentTimeMillis())
                        FailureKind.AUTO_SPEED -> c.copy(autoSpeedFailures = c.autoSpeedFailures + 1, lastFailureAtMs = System.currentTimeMillis())
                        FailureKind.CONNECT -> c.copy(connectFailures = c.connectFailures + 1)
                    }
                }
            }
            if (list.isNotEmpty()) last = list.last()
            if (changed) lock.withLock { flush() }
        }
    }

    private fun mutate(f: (HealthCounters) -> HealthCounters) {
        _counters.update(f)
        dirty = true
    }

    private suspend fun flush() {
        if (!dirty) return
        dirty = false
        try {
            repository.save(_counters.value)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            dirty = true // try again at the next step
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
    }
}
