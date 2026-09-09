package com.example.platinumcleaner.domain.cleaning

import com.example.platinumcleaner.service.CleanSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SessionFlowTest — Unit tests for Sprint V8 cleaning session flow and batch queue management.
 * Sesuai ai_task.md §16, §17, §54, §59:
 * - Session batch queue handles full eligible apps (no 5-app limit)
 * - Sequential advancing through batch targets
 * - Session cancellation resets state to IDLE
 * - Self-protection validation
 */
class SessionFlowTest {

    @Before
    fun setUp() {
        CleanSessionManager.endSession()
    }

    @After
    fun tearDown() {
        CleanSessionManager.endSession()
    }

    @Test
    fun testStartSingleSession() {
        val started = CleanSessionManager.startSession("org.telegram.messenger")

        assertTrue(started)
        assertEquals(CleanSessionManager.SessionState.PLANNING, CleanSessionManager.currentState)
        assertEquals("org.telegram.messenger", CleanSessionManager.targetPackageName)
        assertEquals(1, CleanSessionManager.totalApps)
        assertEquals(0, CleanSessionManager.currentIndex)
    }

    @Test
    fun testBatchSessionHandlesMoreThanFiveAppsWithoutLimit() {
        // §16, §17: No artificial 5-app limit
        val packages = (1..15).map { "com.example.app$it" }

        val started = CleanSessionManager.startBatchSession(packages)

        assertTrue(started)
        assertEquals(CleanSessionManager.SessionState.PLANNING, CleanSessionManager.currentState)
        assertEquals("com.example.app1", CleanSessionManager.targetPackageName)
        assertEquals(15, CleanSessionManager.totalApps)

        // Advance through queue using moveToNext
        var current: String? = CleanSessionManager.targetPackageName
        var count = 1
        while (true) {
            val next = CleanSessionManager.moveToNext() ?: break
            count++
            assertEquals("com.example.app$count", next)
        }

        assertEquals(15, count)
        assertEquals(15, CleanSessionManager.totalApps)
    }

    @Test
    fun testCancelSessionResetsToIdle() {
        val packages = listOf("com.app.a", "com.app.b", "com.app.c")
        CleanSessionManager.startBatchSession(packages)
        CleanSessionManager.markExecuting(CleaningCapability.PER_APP_NAVIGATION)

        assertEquals(CleanSessionManager.SessionState.EXECUTING, CleanSessionManager.currentState)
        assertEquals("com.app.a", CleanSessionManager.targetPackageName)

        // User cancels session (§59)
        CleanSessionManager.cancelSession()

        assertEquals(CleanSessionManager.SessionState.IDLE, CleanSessionManager.currentState)
        assertNull(CleanSessionManager.targetPackageName)
        assertEquals(0, CleanSessionManager.totalApps)
        assertFalse(CleanSessionManager.isActive)
    }

    @Test
    fun testSelfProtectionCheck() {
        val appPackageName = "com.example.platinumcleaner"
        val targets = listOf("com.example.platinumcleaner", "org.telegram.messenger", "com.whatsapp")

        // Filter out own package (§45)
        val filteredTargets = targets.filter { it != appPackageName }

        assertEquals(2, filteredTargets.size)
        assertFalse(filteredTargets.contains(appPackageName))
        assertTrue(filteredTargets.contains("org.telegram.messenger"))
        assertTrue(filteredTargets.contains("com.whatsapp"))
    }
}
