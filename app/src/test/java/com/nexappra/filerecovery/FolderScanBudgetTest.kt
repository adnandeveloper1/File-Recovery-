package com.nexappra.filerecovery

import com.nexappra.filerecovery.data.repository.FolderScanBudget
import org.junit.Assert.*
import org.junit.Test

class FolderScanBudgetTest {
    @Test fun quickDepthLimitDoesNotPreventOtherShallowFolders() {
        val budget = FolderScanBudget(true) { 0L }
        assertTrue(budget.enterDirectory(6))
        assertFalse(budget.enterDirectory(7))
        assertTrue(budget.limited)
        assertTrue(budget.enterDirectory(1))
    }

    @Test fun lastAllowedDirectoryCanStillProcessItsEntries() {
        val budget = FolderScanBudget(true) { 0L }
        repeat(200) { assertTrue(budget.enterDirectory(0)) }
        assertTrue(budget.inspectEntry())
        assertFalse(budget.enterDirectory(0))
        assertTrue(budget.limited)
    }

    @Test fun entryLimitLeavesTheCollectedBatchAvailableForProcessing() {
        val budget = FolderScanBudget(true) { 0L }
        assertTrue(budget.enterDirectory(0))
        repeat(10_000) { assertTrue(budget.inspectEntry()) }
        assertFalse(budget.inspectEntry())
        assertTrue(budget.canContinue())
        assertFalse(budget.enterDirectory(1))
        assertTrue(budget.limited)
    }

    @Test fun quickDeadlineUsesElapsedTimeStartingAtFolderWork() {
        var time = 50_000L
        val budget = FolderScanBudget(true) { time }
        time += 100_000L
        assertTrue(budget.enterDirectory(0))
        time += 14_999L
        assertTrue(budget.canContinue())
        time++
        assertFalse(budget.canContinue())
        assertTrue(budget.limited)
    }

    @Test fun deepSearchKeepsItsLargerDepthAndHasNoQuickEntryOrTimeCap() {
        var time = 0L
        val budget = FolderScanBudget(false) { time }
        repeat(201) { assertTrue(budget.enterDirectory(32)) }
        repeat(10_001) { assertTrue(budget.inspectEntry()) }
        time = Long.MAX_VALUE
        assertTrue(budget.canContinue())
        assertFalse(budget.limited)
        assertFalse(budget.enterDirectory(33))
        assertTrue(budget.limited)
    }
}
