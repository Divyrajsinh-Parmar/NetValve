package dev.netvalve.ui.dashboard

import dev.netvalve.scheduler.RateUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private fun steps(
        running: Boolean = true,
        onlySelected: Boolean = true,
        apps: Int = 1,
        priorityOn: Boolean = true,
        down: Long = 1_000_000L,
        up: Long = 500_000L,
    ) = SetupChecklist.evaluate(running, onlySelected, apps, priorityOn, down, up)

    private fun state(list: List<SetupStep>, id: SetupStepId) = list.first { it.id == id }.state

    @Test
    fun everythingReadyIsAllDone() {
        val s = steps()
        assertEquals(5, s.size)
        assertTrue(SetupChecklist.allDone(s))
        assertEquals(5, SetupChecklist.doneCount(s))
    }

    @Test
    fun stepsComeInTheOrderAPersonShouldDoThem() {
        assertEquals(
            listOf(SetupStepId.TUNNEL, SetupStepId.ONLY_SELECTED, SetupStepId.APPS, SetupStepId.SPEEDS, SetupStepId.PRIORITY),
            steps().map { it.id },
        )
    }

    @Test
    fun freshInstallNeedsEverything() {
        val s = steps(running = false, apps = 0, priorityOn = false, down = 0, up = 0)
        assertEquals(StepState.TODO, state(s, SetupStepId.TUNNEL))
        assertEquals(StepState.TODO, state(s, SetupStepId.APPS))
        assertEquals(StepState.TODO, state(s, SetupStepId.SPEEDS))
        assertEquals(StepState.TODO, state(s, SetupStepId.PRIORITY))
        assertFalse(SetupChecklist.allDone(s))
    }

    @Test
    fun noAppsTickedIsOnlyATodoInOnlySelectedMode() {
        assertEquals(StepState.TODO, state(steps(apps = 0, onlySelected = true), SetupStepId.APPS))
        assertEquals(StepState.DONE, state(steps(apps = 0, onlySelected = false), SetupStepId.APPS))
    }

    @Test
    fun allAppsModeIsAWarningNotAnError() {
        assertEquals(StepState.WARN, state(steps(onlySelected = false), SetupStepId.ONLY_SELECTED))
        assertFalse(SetupChecklist.allDone(steps(onlySelected = false)))
    }

    @Test
    fun sharedSpeedForOneDirectionOnlyIsAWarning() {
        assertEquals(StepState.WARN, state(steps(down = 1_000_000L, up = 0L), SetupStepId.SPEEDS))
        assertEquals(StepState.WARN, state(steps(down = 0L, up = 1_000_000L), SetupStepId.SPEEDS))
        assertEquals(StepState.TODO, state(steps(down = 0L, up = 0L), SetupStepId.SPEEDS))
    }

    @Test
    fun priorityOffIsATodo() {
        assertEquals(StepState.TODO, state(steps(priorityOn = false), SetupStepId.PRIORITY))
    }

    @Test
    fun ninetyPercentTextRoundsToOneDecimal() {
        assertEquals("22.5", RateUnits.shareTextOf("25"))
        assertEquals("90", RateUnits.shareTextOf("100"))
        assertEquals("9", RateUnits.shareTextOf("10"))
        assertEquals("22.5", RateUnits.shareTextOf(" 25 "))
        assertEquals("11.3", RateUnits.shareTextOf("12,5"))
    }

    @Test
    fun ninetyPercentTextRejectsBadInput() {
        assertNull(RateUnits.shareTextOf(""))
        assertNull(RateUnits.shareTextOf("abc"))
        assertNull(RateUnits.shareTextOf("0"))
        assertNull(RateUnits.shareTextOf("-5"))
        assertNull(RateUnits.shareTextOf("0.01"))
    }
}
