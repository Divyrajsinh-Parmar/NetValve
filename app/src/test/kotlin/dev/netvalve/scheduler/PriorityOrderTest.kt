package dev.netvalve.scheduler

import dev.netvalve.scheduler.PriorityOrder.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityOrderTest {

    private val locked = mapOf("probe" to PriorityLevels.PROBE, "self" to PriorityLevels.NORMAL)

    @Test
    fun firstAppOfTheListGetsTheFirstRank() {
        val o = PriorityOrder(listOf("a", "b", "c"), locked)
        assertEquals(PriorityLevels.FIRST_RANK, o.levelOf("a"))
        assertEquals(PriorityLevels.FIRST_RANK + 1, o.levelOf("b"))
        assertEquals(PriorityLevels.FIRST_RANK + 2, o.levelOf("c"))
        assertEquals(1, o.rankOf("a"))
        assertEquals(0, o.rankOf("zzz"))
    }

    @Test
    fun appsOutsideTheListAreNormal() {
        val o = PriorityOrder(listOf("a"), locked)
        assertEquals(PriorityLevels.NORMAL, o.levelOf("other"))
    }

    @Test
    fun lockedEntriesKeepTheirLevelAndCannotBeChanged() {
        val o = PriorityOrder(emptyList(), locked)
        assertEquals(PriorityLevels.PROBE, o.levelOf("probe"))
        assertEquals(PriorityLevels.NORMAL, o.levelOf("self"))
        assertEquals(Result.LOCKED, o.add("probe"))
        assertEquals(Result.LOCKED, o.remove("probe"))
        assertEquals(Result.LOCKED, o.moveUp("probe"))
    }

    @Test
    fun theListHoldsAtMostMaxRanksApps() {
        val o = PriorityOrder(emptyList(), locked)
        for (i in 1..PriorityLevels.MAX_RANKS) assertEquals(Result.OK, o.add("app$i"))
        assertEquals(Result.FULL, o.add("one.too.many"))
        assertEquals(PriorityLevels.MAX_RANKS, o.size())
    }

    @Test
    fun addTwiceIsReportedAndKeepsTheOldPlace() {
        val o = PriorityOrder(listOf("a", "b"), locked)
        assertEquals(Result.ALREADY_IN_LIST, o.add("a"))
        assertEquals(listOf("a", "b"), o.snapshot())
    }

    @Test
    fun moveUpAndDownSwapNeighboursAndStopAtTheEdges() {
        val o = PriorityOrder(listOf("a", "b", "c"), locked)
        assertEquals(Result.OK, o.moveUp("c"))
        assertEquals(listOf("a", "c", "b"), o.snapshot())
        assertEquals(Result.OK, o.moveDown("a"))
        assertEquals(listOf("c", "a", "b"), o.snapshot())
        assertEquals(Result.AT_EDGE, o.moveUp("c"))
        assertEquals(Result.AT_EDGE, o.moveDown("b"))
        assertEquals(Result.NOT_IN_LIST, o.moveUp("zzz"))
        // the new order changes the levels at once
        assertEquals(PriorityLevels.FIRST_RANK, o.levelOf("c"))
    }

    @Test
    fun removeFreesThePlaceAndTheAppBecomesNormal() {
        val o = PriorityOrder(listOf("a", "b", "c"), locked)
        assertEquals(Result.OK, o.remove("b"))
        assertEquals(listOf("a", "c"), o.snapshot())
        assertEquals(PriorityLevels.NORMAL, o.levelOf("b"))
        assertEquals(PriorityLevels.FIRST_RANK + 1, o.levelOf("c"))
        assertEquals(Result.NOT_IN_LIST, o.remove("b"))
    }

    @Test
    fun sharedUidTakesTheMostImportantLevel() {
        val o = PriorityOrder(listOf("a", "b"), locked)
        assertEquals(PriorityLevels.FIRST_RANK, o.levelOfPackages(listOf("b", "a", "x")))
        assertEquals(PriorityLevels.NORMAL, o.levelOfPackages(listOf("x", "y")))
        assertEquals(PriorityLevels.NORMAL, o.levelOfPackages(emptyList()))
    }

    @Test
    fun initialListIgnoresLockedDuplicatesAndOverflow() {
        val o = PriorityOrder(listOf("a", "probe", "a", "b", "c", "d", "e", "f", "g"), locked)
        assertEquals(listOf("a", "b", "c", "d", "e"), o.snapshot())
    }
}
