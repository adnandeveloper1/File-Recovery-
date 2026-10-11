package com.nexappra.filerecovery.data.repository

/** Shared across Quick Scan's folder sources; limits do not discard already found files. */
internal class FolderScanBudget(
    private val quick: Boolean,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var started: Long? = null
    private var directories = 0
    private var entries = 0
    var limited = false
        private set

    fun canContinue(): Boolean {
        if (!quick) return true
        val now = nowMillis()
        if (started == null) started = now
        val allowed = now - started!! < 15_000L
        if (!allowed) limited = true
        return allowed
    }

    fun canSearchDirectory(): Boolean {
        if (quick && (directories >= 200 || entries >= 10_000)) {
            limited = true
            return false
        }
        return canContinue()
    }

    fun enterDirectory(depth: Int): Boolean {
        if (depth > if (quick) 6 else 32) {
            limited = true
            return false
        }
        if (!canSearchDirectory()) return false
        directories++
        return true
    }

    fun inspectEntry(): Boolean {
        if (quick && entries >= 10_000) {
            limited = true
            return false
        }
        if (!canContinue()) return false
        entries++
        return true
    }
}
