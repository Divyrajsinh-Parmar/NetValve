package dev.netvalve.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-time tests of the coroutine adapter (short, generous tolerances so they are not flaky). */
class SchedulerPacerTest {

    private val rate = 200_000L // 200 kB/s
    private fun cfg() = SchedulerConfig(mode = SchedMode.PRIORITY_PACER, rateBytesPerSec = rate)

    private fun newPacer(scope: CoroutineScope) = SchedulerPacer(scope, cfg(), cfg())

    @Test
    fun acquireTakesAboutBytesOverRate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val t0 = System.nanoTime()
        pacer.acquire(Tier.LOW, 1, Lane.UPLOAD, 100_000) // 0.5 s at 200 kB/s
        val ms = (System.nanoTime() - t0) / 1_000_000
        scope.cancel()
        assertTrue("took $ms ms", ms in 380..800)
    }

    @Test
    fun highRequestOvertakesSaturatingLowFlows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val lows = (1..3).map { id ->
            scope.launch { while (true) pacer.acquire(Tier.LOW, id.toLong(), Lane.UPLOAD, 16 * 1024) }
        }
        delay(500) // let the Low flows saturate the pacer
        val t0 = System.nanoTime()
        withTimeout(2_000) { pacer.acquire(Tier.HIGH, 99, Lane.UPLOAD, 1_400) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        lows.forEach { it.cancelAndJoin() }
        scope.cancel()
        assertTrue("High waited $ms ms behind saturating Low flows", ms <= 150)
    }

    @Test
    fun cancelledAcquireReleasesItsBudget() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val big = scope.launch { pacer.acquire(Tier.LOW, 1, Lane.UPLOAD, 50_000_000) } // would take minutes
        delay(200)
        big.cancelAndJoin()
        val t0 = System.nanoTime()
        withTimeout(2_000) { pacer.acquire(Tier.LOW, 2, Lane.UPLOAD, 20_000) } // 0.1 s if the budget is free again
        val ms = (System.nanoTime() - t0) / 1_000_000
        scope.cancel()
        assertTrue("took $ms ms", ms <= 600)
    }

    @Test
    fun lanesAreIndependent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        val up = scope.async { pacer.acquire(Tier.LOW, 1, Lane.UPLOAD, 100_000) }
        val t0 = System.nanoTime()
        pacer.acquire(Tier.LOW, 2, Lane.DOWNLOAD, 20_000) // download lane is free
        val ms = (System.nanoTime() - t0) / 1_000_000
        up.cancelAndJoin()
        scope.cancel()
        assertTrue("download waited $ms ms", ms <= 400)
    }

    @Test
    fun rateCanBeChangedInPlace() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pacer = newPacer(scope)
        pacer.updateConfig(Lane.UPLOAD, cfg().copy(rateBytesPerSec = 1_000_000))
        val t0 = System.nanoTime()
        pacer.acquire(Tier.LOW, 1, Lane.UPLOAD, 200_000) // 0.2 s at the new rate, 1 s at the old one
        val ms = (System.nanoTime() - t0) / 1_000_000
        scope.cancel()
        assertTrue("took $ms ms", ms in 100..500)
    }
}
