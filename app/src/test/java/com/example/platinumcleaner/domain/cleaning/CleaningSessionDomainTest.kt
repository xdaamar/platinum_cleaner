package com.example.platinumcleaner.domain.cleaning

import com.example.platinumcleaner.service.CleanSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CleaningSessionDomainTest — Unit tests for Sprint V9 Cleaning Domain & State Machine.
 * Sesuai ai_task.md V9 (§2, §3, §4, §5, §7, §8, §18, §19, §25, §26, §30, §59, §82):
 * - CleaningSession deterministic transitions
 * - Skip target behavior (USER_SKIPPED without failure)
 * - Global Stop behavior
 * - Verified reclaim aggregation
 * - CleanSessionManager integration
 */
class CleaningSessionDomainTest {

    @Before
    fun setUp() {
        CleanSessionManager.endSession()
    }

    @After
    fun tearDown() {
        CleanSessionManager.endSession()
    }

    @Test
    fun testCleaningSessionInitialState() {
        val targets = listOf(
            CleaningTarget("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L, isEligible = true),
            CleaningTarget("com.whatsapp", "WhatsApp", 250 * 1024 * 1024L, isEligible = true)
        )

        val session = CleaningSession(
            targets = targets,
            currentIndex = 0,
            mode = CleaningMode.PER_APP_AUTOMATED,
            state = CleaningSessionState.PREPARING
        )

        assertEquals(2, session.totalTargets)
        assertEquals("org.telegram.messenger", session.currentTarget?.packageName)
        assertTrue(session.isRunning)
        assertEquals(0, session.successCount)
        assertEquals(0L, session.totalReclaimedBytes)
    }

    @Test
    fun testCleaningSessionNextTargetProgression() {
        val targets = listOf(
            CleaningTarget("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L, isEligible = true),
            CleaningTarget("com.whatsapp", "WhatsApp", 250 * 1024 * 1024L, isEligible = true)
        )

        var session = CleaningSession(
            targets = targets,
            currentIndex = 0,
            mode = CleaningMode.PER_APP_AUTOMATED,
            state = CleaningSessionState.PREPARING
        )

        // Record success for first target
        session = session.recordResult("org.telegram.messenger", VerificationStatus.VERIFIED_SUCCESS, 800 * 1024 * 1024L)
        assertEquals(CleaningSessionState.SUCCESS, session.state)
        assertEquals(1, session.successCount)
        assertEquals(800 * 1024 * 1024L, session.totalReclaimedBytes)

        // Advance to second target
        session = session.nextTarget()
        assertEquals(1, session.currentIndex)
        assertEquals("com.whatsapp", session.currentTarget?.packageName)
        assertEquals(CleaningSessionState.PREPARING, session.state)

        // Record partial success for second target
        session = session.recordResult("com.whatsapp", VerificationStatus.VERIFIED_PARTIAL, 200 * 1024 * 1024L)
        assertEquals(2, session.successCount)
        assertEquals(1000 * 1024 * 1024L, session.totalReclaimedBytes)

        // Next target reaches completion
        session = session.nextTarget()
        assertEquals(CleaningSessionState.COMPLETED, session.state)
        assertFalse(session.isRunning)
    }

    @Test
    fun testUserSkipBehavior() {
        // ai_task.md §26: Skip current target moves to next without being treated as failure
        val targets = listOf(
            CleaningTarget("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L, isEligible = true),
            CleaningTarget("com.whatsapp", "WhatsApp", 250 * 1024 * 1024L, isEligible = true)
        )

        var session = CleaningSession(
            targets = targets,
            currentIndex = 0,
            mode = CleaningMode.PER_APP_ASSISTED,
            state = CleaningSessionState.WAITING_FOR_USER
        )

        session = session.skipCurrentTarget()

        assertEquals(1, session.currentIndex)
        assertEquals("com.whatsapp", session.currentTarget?.packageName)
        assertEquals(1, session.skippedCount)
        assertEquals(0, session.failedCount)
        assertEquals(VerificationStatus.USER_SKIPPED, session.targetResults["org.telegram.messenger"])
    }

    @Test
    fun testUserStopBehavior() {
        // ai_task.md §8 & §30: Global stop request
        val targets = listOf(
            CleaningTarget("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L, isEligible = true),
            CleaningTarget("com.whatsapp", "WhatsApp", 250 * 1024 * 1024L, isEligible = true)
        )

        var session = CleaningSession(
            targets = targets,
            currentIndex = 0,
            mode = CleaningMode.PER_APP_AUTOMATED,
            state = CleaningSessionState.AUTOMATING
        )

        session = session.requestStop()
        assertEquals(CleaningSessionState.STOP_REQUESTED, session.state)

        session = session.markStopped()
        assertEquals(CleaningSessionState.STOPPED, session.state)
        assertFalse(session.isRunning)
    }

    @Test
    fun testCleanSessionManagerStopAndSkipIntegration() {
        val targets = listOf(
            CleaningTarget("com.app.first", "First", 100L, isEligible = true),
            CleaningTarget("com.app.second", "Second", 200L, isEligible = true),
            CleaningTarget("com.app.third", "Third", 300L, isEligible = true)
        )

        CleanSessionManager.startBatchSessionWithTargets(targets, CleaningMode.PER_APP_ASSISTED)
        assertTrue(CleanSessionManager.isActive)
        assertNotNull(CleanSessionManager.activeSession)
        assertEquals("com.app.first", CleanSessionManager.targetPackageName)

        // Skip first app
        val nextApp = CleanSessionManager.skipCurrent()
        assertEquals("com.app.second", nextApp)
        assertEquals("com.app.second", CleanSessionManager.targetPackageName)

        // Request stop while second app is active
        CleanSessionManager.requestStop()
        assertTrue(CleanSessionManager.isStopRequested)

        // Moving to next should now stop the session
        val afterStop = CleanSessionManager.moveToNext()
        assertNull(afterStop)
        assertFalse(CleanSessionManager.isActive)
        assertEquals(CleanSessionManager.SessionState.IDLE, CleanSessionManager.currentState)
    }
}
