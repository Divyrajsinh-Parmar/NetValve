package dev.netvalve.demo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class DemoOrchestratorTest {

    private class FakeControls(
        private val enterOk: Boolean = true,
        private val pauseOk: Boolean = true,
        private val resumeOk: Boolean = true,
        private val leaveThrows: Boolean = false,
    ) : DemoControls {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        override suspend fun enter(): Boolean { calls += "enter"; return enterOk }
        override suspend fun pause(): Boolean { calls += "pause"; return pauseOk }
        override suspend fun resume(): Boolean { calls += "resume"; return resumeOk }
        override suspend fun leave() { calls += "leave"; if (leaveThrows) error("leave failed") }
    }

    private class FakeLoad(private val bytesPerSec: Long = 10_000L, private val problemText: String? = null) : LoadGenerator {
        @Volatile var starts = 0
        @Volatile var stops = 0
        @Volatile private var startedAt = 0L
        @Volatile private var running = false
        override fun start() { starts++; running = true; startedAt = System.nanoTime() }
        override fun stop() { stops++; running = false }
        override fun totalBytes(): Long = if (running) (System.nanoTime() - startedAt) / 1_000_000L * bytesPerSec / 1000L else 0L
        override fun problem(): String? = problemText
    }

    private class FakeProber(private val answer: (Int) -> Long?) : Prober {
        @Volatile var n = 0
        override suspend fun probeOnce(): Long? = answer(n++)
    }

    private val fast = DemoTimings(idleMs = 150, loadedMs = 400, warmupMs = 100, probeEveryMs = 20)

    @Test
    fun fullRunGivesThreePhasesAndPutsEverythingBack() = runBlocking {
        val controls = FakeControls()
        val load = FakeLoad()
        val out = DemoOrchestrator(controls, load, FakeProber { 40L }, fast).run { }
        assertEquals(DemoStep.DONE, out.step)
        assertEquals(listOf("enter", "pause", "resume", "leave"), controls.calls)
        assertEquals(listOf(DemoPhase.IDLE, DemoPhase.BEFORE, DemoPhase.AFTER), out.results.map { it.phase })
        assertEquals(2, load.starts)
        assertTrue(load.stops >= 2)
        assertTrue(out.results.all { it.stats.sent > 0 })
        assertEquals(0L, out.results[0].loadBytesPerSec)
        // about 10 000 bytes/s, wide tolerance for a loaded machine
        assertTrue("speed ${out.results[1].loadBytesPerSec}", out.results[1].loadBytesPerSec in 5_000..15_000)
    }

    @Test
    fun warmupAnswersAreNotCounted() = runBlocking {
        val out = DemoOrchestrator(FakeControls(), FakeLoad(), FakeProber { 40L }, fast).run { }
        val idle = out.results[0].stats.sent
        val before = out.results[1].stats.sent
        // idle counts all of 150 ms; a loaded phase counts only 300 of its 400 ms
        assertTrue("idle $idle before $before", idle in 4..9)
        assertTrue("before $before", before in 8..16)
    }

    @Test
    fun resultsFollowTheAnswers() = runBlocking {
        val calls = FakeControls()
        // quiet line fast, but slow while loaded: the prober cannot know the phase, so use the call count
        val out = DemoOrchestrator(calls, FakeLoad(), FakeProber { n -> if (n < 8) 30L else null }, fast).run { }
        assertEquals(DemoStep.DONE, out.step)
        assertTrue(out.results.last().stats.lost > 0)
    }

    @Test
    fun failedEnterStopsAtOnceButStillLeaves() = runBlocking {
        val controls = FakeControls(enterOk = false)
        val load = FakeLoad()
        val out = DemoOrchestrator(controls, load, FakeProber { 1L }, fast).run { }
        assertEquals(DemoStep.FAILED, out.step)
        assertEquals(listOf("enter", "leave"), controls.calls)
        assertEquals(0, load.starts)
    }

    @Test
    fun failedPauseKeepsTheQuietResultAndLeaves() = runBlocking {
        val controls = FakeControls(pauseOk = false)
        val out = DemoOrchestrator(controls, FakeLoad(), FakeProber { 1L }, fast).run { }
        assertEquals(DemoStep.FAILED, out.step)
        assertEquals(1, out.results.size)
        assertEquals("leave", controls.calls.last())
    }

    @Test
    fun leaveThatThrowsDoesNotCrashTheRun() = runBlocking {
        val out = DemoOrchestrator(FakeControls(leaveThrows = true), FakeLoad(), FakeProber { 1L }, fast).run { }
        assertEquals(DemoStep.DONE, out.step)
    }

    @Test
    fun aProbeThatThrowsFailsTheRunButRestores() = runBlocking {
        val controls = FakeControls()
        val load = FakeLoad()
        val out = DemoOrchestrator(controls, load, FakeProber { n -> if (n > 3) error("boom") else 5L }, fast).run { }
        assertEquals(DemoStep.FAILED, out.step)
        assertEquals("leave", controls.calls.last())
        assertTrue(out.message!!.contains("boom"))
    }

    @Test
    fun cancelDuringTheRunStillRestores() = runBlocking {
        val controls = FakeControls()
        val load = FakeLoad()
        val job = launch(Dispatchers.Default) {
            DemoOrchestrator(controls, load, FakeProber { 5L }, DemoTimings(idleMs = 60_000)).run { }
        }
        delay(200)
        job.cancel()
        job.join()
        assertTrue(controls.calls.contains("leave"))
        assertTrue(load.stops >= 1)
    }

    @Test
    fun reportContainsThePhasesAndTheLimits() = runBlocking {
        val out = DemoOrchestrator(FakeControls(), FakeLoad(), FakeProber { 40L }, fast).run { }
        val text = DemoReport.text(out.results)
        assertTrue(text, text.contains("BEFORE (priority paused, tunnel still on"))
        assertTrue(text, text.contains("AFTER (priority on"))
        assertTrue(text, text.contains("our own choice"))
        assertTrue(text, text.contains("not the game's own ping"))
    }

    @Test
    fun aDownloadThatDidNotRunMakesTheResultNotValid() = runBlocking {
        val out = DemoOrchestrator(FakeControls(), FakeLoad(bytesPerSec = 0L, problemText = "3 download error(s), last: IOException: boom"), FakeProber { 40L }, fast).run { }
        assertEquals(DemoStep.DONE, out.step)
        assertTrue(out.results[0].valid) // the quiet line has no download
        assertTrue(!out.results[1].valid)
        assertTrue(!out.results[2].valid)
        assertTrue(!out.valid)
        assertEquals("3 download error(s), last: IOException: boom", out.results[1].loadProblem)
        val text = DemoReport.text(out.results)
        assertTrue(text, text.contains("RESULT NOT VALID"))
        assertTrue(text, text.contains("(NOT VALID)"))
        assertTrue(text, text.contains("download problem: 3 download error(s)"))
    }

    @Test
    fun aRunningDownloadIsValid() = runBlocking {
        val out = DemoOrchestrator(FakeControls(), FakeLoad(bytesPerSec = 1_000_000L), FakeProber { 40L }, fast).run { }
        assertTrue(out.valid)
        assertTrue(!DemoReport.text(out.results).contains("NOT VALID"))
    }

    @Test
    fun aProblemThatThrowsIsIgnored() = runBlocking {
        val load = object : LoadGenerator {
            override fun start() {}
            override fun stop() {}
            override fun totalBytes(): Long = 0L
            override fun problem(): String? = error("no")
        }
        val out = DemoOrchestrator(FakeControls(), load, FakeProber { 40L }, fast).run { }
        assertEquals(DemoStep.DONE, out.step)
    }
}
