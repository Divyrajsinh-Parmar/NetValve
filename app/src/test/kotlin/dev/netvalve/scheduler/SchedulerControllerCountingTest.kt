package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-level counters of the controller (patch B3). */
class SchedulerControllerCountingTest {

    private val order = PriorityOrder(listOf("high.app"))
    private val resolver = LevelResolver(order) { uid ->
        when (uid) {
            HIGH_UID -> listOf("high.app")
            LOW_UID -> listOf("low.app")
            else -> emptyList()
        }
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun controller(scope: CoroutineScope, settings: Flow<SchedulerSettings>) =
        SchedulerController(scope, settings, resolver) { System.nanoTime() / 1_000_000L }

    @Test
    fun bytesAreCountedPerLevelEvenWhenTheSchedulerIsOff() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings()))
            runBlocking {
                withTimeout(2_000) {
                    c.acquire(HIGH_UID, 1L, Lane.DOWNLOAD, 1_000)
                    c.acquire(LOW_UID, 2L, Lane.DOWNLOAD, 3_000)
                    c.acquire(LOW_UID, 2L, Lane.UPLOAD, 500)
                    c.acquire(-1, 3L, Lane.DOWNLOAD, 70)
                }
            }
            val t = c.totals()
            assertEquals(1_000L, t.downBytes[1])
            assertEquals(3_070L, t.downBytes[PriorityLevels.NORMAL])
            assertEquals(500L, t.upBytes[PriorityLevels.NORMAL])
            assertEquals(70L, t.unattributedBytes)
            // the scheduler is off: no wait is measured
            assertEquals(0L, t.waitCount[1])
        } finally { scope.cancel() }
    }

    @Test
    fun waitIsMeasuredWhileTheSchedulerIsActive() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings(SchedMode.PRIORITY_PACER, 1_000_000L, 1_000_000L)))
            runBlocking {
                val end = System.nanoTime() + 2_000_000_000L
                while (!c.isActive(Lane.DOWNLOAD) && System.nanoTime() < end) delay(10)
                withTimeout(5_000) { c.acquire(LOW_UID, 1L, Lane.DOWNLOAD, 500_000) }
            }
            val t = c.totals()
            assertEquals(1L, t.waitCount[PriorityLevels.NORMAL])
            assertTrue("wait ${t.waitMsSum[PriorityLevels.NORMAL]} ms", t.waitMsSum[PriorityLevels.NORMAL] in 200..3_000)
            assertEquals(500_000L, t.downBytes[PriorityLevels.NORMAL])
        } finally { scope.cancel() }
    }

    @Test
    fun aFailingPackageLookupDoesNotStopTheFlow() {
        val scope = scope()
        try {
            val broken = LevelResolver(order) { error("package lookup failed") }
            val c = SchedulerController(scope, MutableStateFlow(SchedulerSettings()), broken) { System.nanoTime() / 1_000_000L }
            runBlocking { withTimeout(2_000) { c.acquire(10_050, 1L, Lane.DOWNLOAD, 100) } }
            assertEquals(100L, c.totals().downBytes[PriorityLevels.NORMAL])
        } finally { scope.cancel() }
    }

    private companion object {
        const val HIGH_UID = 10_002
        const val LOW_UID = 10_001
    }
}
