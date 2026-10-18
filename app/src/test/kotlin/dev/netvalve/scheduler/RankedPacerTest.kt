package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-time tests of the ranked coroutine adapter (short, generous tolerances so they are not flaky). */
class RankedPacerTest {

    private val rate = 200_000L // 200 kB/s
    private fun cfg() = SchedulerConfig(mode = SchedMode.PRIORITY_PACER, rateBytesPerSec = rate)
    private fun newPacer(scope: CoroutineScope) = RankedPacer(scope, cfg(), cfg())

    @Test
    fun acquireTakesAboutBytesOverRate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val t0 = System.nanoTime()
        pacer.acquire(PriorityLevels.NORMAL, 1, Lane.UPLOAD, 100_000) // 0.5 s at 200 kB/s
        val ms = (System.nanoTime() - t0) / 1_000_000
        scope.cancel()
        assertTrue("took $ms ms", ms in 380..800)
    }

    @Test
    fun firstRankRequestOvertakesSaturatingNormalFlows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val normals = (1..3).map { id ->
            scope.launch { while (true) pacer.acquire(PriorityLevels.NORMAL, id.toLong(), Lane.UPLOAD, 16 * 1024) }
        }
        delay(500)
        val t0 = System.nanoTime()
        withTimeout(2_000) { pacer.acquire(PriorityLevels.FIRST_RANK, 99, Lane.UPLOAD, 1_400) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        normals.forEach { it.cancelAndJoin() }
        scope.cancel()
        assertTrue("first rank waited $ms ms behind saturating normal flows", ms <= 150)
    }

    @Test
    fun secondRankOvertakesNormalButNotTheFirstRank() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val normals = (1..2).map { id ->
            scope.launch { while (true) pacer.acquire(PriorityLevels.NORMAL, id.toLong(), Lane.DOWNLOAD, 16 * 1024) }
        }
        delay(400)
        val t0 = System.nanoTime()
        withTimeout(2_000) { pacer.acquire(PriorityLevels.FIRST_RANK + 1, 50, Lane.DOWNLOAD, 1_400) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        normals.forEach { it.cancelAndJoin() }
        scope.cancel()
        assertTrue("second rank waited $ms ms", ms <= 150)
    }

    @Test
    fun cancelledAcquireReleasesItsBudget() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val big = scope.launch { pacer.acquire(PriorityLevels.NORMAL, 1, Lane.UPLOAD, 50_000_000) }
        delay(200)
        big.cancelAndJoin()
        val t0 = System.nanoTime()
        withTimeout(2_000) { pacer.acquire(PriorityLevels.NORMAL, 2, Lane.UPLOAD, 20_000) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        scope.cancel()
        assertTrue("took $ms ms", ms <= 600)
    }
}
