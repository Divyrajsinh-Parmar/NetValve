package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-time tests with generous tolerances (the pacer ticks every 10 ms). */
class SchedulerControllerTest {

    // "high.app" is the first app of the priority list (level 1); "low.app" is not in the list (normal).
    private val order = PriorityOrder(listOf("high.app"))
    private val resolver = LevelResolver(order) { uid ->
        when (uid) {
            LOW_UID -> listOf("low.app")
            HIGH_UID -> listOf("high.app")
            else -> emptyList()
        }
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun controller(scope: CoroutineScope, settings: Flow<SchedulerSettings>) =
        SchedulerController(scope, settings, resolver) { System.nanoTime() / 1_000_000L }

    private suspend fun waitUntil(timeoutMs: Long = 2_000, cond: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!cond() && System.nanoTime() < end) delay(10)
    }

    @Test
    fun offModeReturnsAtOnceAndQueuesNothing() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings()))
            runBlocking { withTimeout(1_000) { c.acquire(LOW_UID, c.newFlowId(), Lane.UPLOAD, 1_000_000) } }
            assertFalse(c.isActive(Lane.UPLOAD))
        } finally { scope.cancel() }
    }

    @Test
    fun directionWithoutRateIsNotScheduled() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings(SchedMode.PRIORITY_PACER, 0L, 1_000_000L)))
            runBlocking {
                waitUntil { c.isActive(Lane.DOWNLOAD) }
                assertFalse(c.isActive(Lane.UPLOAD))
                assertTrue(c.isActive(Lane.DOWNLOAD))
                withTimeout(1_000) { c.acquire(LOW_UID, c.newFlowId(), Lane.UPLOAD, 500_000) }
            }
        } finally { scope.cancel() }
    }

    @Test
    fun settingsChangesAreFollowed() {
        val scope = scope()
        try {
            val flow = MutableStateFlow(SchedulerSettings())
            val c = controller(scope, flow)
            runBlocking {
                flow.value = SchedulerSettings(SchedMode.PRIORITY_PACER, 1_000_000L, 1_000_000L)
                waitUntil { c.isActive(Lane.UPLOAD) }
                assertTrue(c.isActive(Lane.UPLOAD))
                flow.value = SchedulerSettings()
                waitUntil { !c.isActive(Lane.UPLOAD) }
                assertFalse(c.isActive(Lane.UPLOAD))
            }
        } finally { scope.cancel() }
    }

    @Test
    fun activeModeLimitsTheRate() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings(SchedMode.PRIORITY_PACER, 1_000_000L, 1_000_000L)))
            runBlocking {
                waitUntil { c.isActive(Lane.DOWNLOAD) }
                val start = System.nanoTime()
                withTimeout(5_000) { c.acquire(LOW_UID, c.newFlowId(), Lane.DOWNLOAD, 500_000) }
                val ms = (System.nanoTime() - start) / 1_000_000
                // 500 kB at 1 MB/s is about 500 ms; allow a wide window for slow machines.
                assertTrue("took $ms ms, expected about 500", ms in 300..3_000)
            }
        } finally { scope.cancel() }
    }

    @Test
    fun highOvertakesALowFlow() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings(SchedMode.PRIORITY_PACER, 1_000_000L, 1_000_000L)))
            runBlocking {
                waitUntil { c.isActive(Lane.DOWNLOAD) }
                val lowFlow = c.newFlowId()
                val low = async(Dispatchers.Default) { c.acquire(LOW_UID, lowFlow, Lane.DOWNLOAD, 600_000) }
                delay(100) // let Low build a backlog
                val start = System.nanoTime()
                withTimeout(3_000) { c.acquire(HIGH_UID, c.newFlowId(), Lane.DOWNLOAD, 2_000) }
                val highMs = (System.nanoTime() - start) / 1_000_000
                assertTrue("High waited $highMs ms behind Low", highMs < 400)
                assertFalse("Low must still be running when High is done", low.isCompleted)
                low.await()
            }
        } finally { scope.cancel() }
    }

    @Test
    fun describeFlowNamesLevelModeAndRates() {
        val scope = scope()
        try {
            val c = controller(scope, MutableStateFlow(SchedulerSettings(SchedMode.PRIORITY_PACER, 500_000L, 1_000_000L)))
            runBlocking { waitUntil { c.isActive(Lane.UPLOAD) } }
            val line = c.describeFlow(HIGH_UID, 7L)
            assertTrue(line, "flow=7" in line && "level=1" in line && "mode=PRIORITY_PACER" in line)
            assertTrue(line, "R_down=8 Mbps" in line && "R_up=4 Mbps" in line)
            assertEquals(1L, c.newFlowId())
        } finally { scope.cancel() }
    }

    private companion object {
        const val LOW_UID = 10_001
        const val HIGH_UID = 10_002
    }
}
