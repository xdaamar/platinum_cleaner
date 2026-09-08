package com.example.platinumcleaner.domain.cleaning

import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests untuk CleaningResult domain model.
 * Sesuai ai_task.md §28: unit test result classification.
 */
class CleaningResultTest {

    private fun makeResult(
        status: VerificationStatus,
        beforeBytes: Long = 100_000L,
        afterBytes: Long = 0L,
        capability: CleaningCapability = CleaningCapability.PER_APP_NAVIGATION
    ) = AppCleanResult(
        packageName = "com.example.app",
        appName = "Example App",
        beforeBytes = beforeBytes,
        afterBytes = afterBytes,
        status = status,
        usedCapability = capability
    )

    @Test
    fun `reclaimedBytes returns correct value`() {
        val result = makeResult(VerificationStatus.VERIFIED_SUCCESS, 100_000L, 10_000L)
        assertEquals(90_000L, result.reclaimedBytes)
    }

    @Test
    fun `reclaimedBytes returns 0 when afterBytes greater than before`() {
        val result = makeResult(VerificationStatus.NO_CHANGE, 100_000L, 110_000L)
        assertEquals(0L, result.reclaimedBytes)
    }

    @Test
    fun `isCacheReduced is true when reclaimedBytes positive`() {
        val result = makeResult(VerificationStatus.VERIFIED_SUCCESS, 100_000L, 10_000L)
        assertTrue(result.isCacheReduced)
    }

    @Test
    fun `CleaningResult totalReclaimedBytes sums all app results`() {
        val results = listOf(
            makeResult(VerificationStatus.VERIFIED_SUCCESS, 100_000L, 0L),
            makeResult(VerificationStatus.PARTIAL_SUCCESS, 50_000L, 10_000L)
        )
        val session = CleaningResult(
            requestId = "test-001",
            appResults = results,
            selectedCapability = CleaningCapability.PER_APP_NAVIGATION,
            overallStatus = VerificationStatus.VERIFIED_SUCCESS
        )
        assertEquals(140_000L, session.totalReclaimedBytes)
    }

    @Test
    fun `CleaningResult successCount counts only success states`() {
        val results = listOf(
            makeResult(VerificationStatus.VERIFIED_SUCCESS),
            makeResult(VerificationStatus.PARTIAL_SUCCESS),
            makeResult(VerificationStatus.NO_CHANGE),
            makeResult(VerificationStatus.FAILED)
        )
        val session = CleaningResult(
            requestId = "test-002",
            appResults = results,
            selectedCapability = CleaningCapability.PER_APP_NAVIGATION,
            overallStatus = VerificationStatus.PARTIAL_SUCCESS
        )
        assertEquals(2, session.successCount) // VERIFIED + PARTIAL
        assertEquals(1, session.noChangeCount)
        assertEquals(1, session.failedCount)
    }

    @Test
    fun `CleaningCapability priority order is correct`() {
        assertTrue(
            CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST.priority <
                    CleaningCapability.PER_APP_NAVIGATION.priority
        )
        assertTrue(
            CleaningCapability.PER_APP_NAVIGATION.priority <
                    CleaningCapability.ACCESSIBILITY_AUTOMATION.priority
        )
        assertTrue(
            CleaningCapability.ACCESSIBILITY_AUTOMATION.priority <
                    CleaningCapability.UNSUPPORTED.priority
        )
    }

    @Test
    fun `CleaningRequest has unique requestId`() {
        val req1 = CleaningRequest(targetPackages = listOf("com.a.b"))
        val req2 = CleaningRequest(targetPackages = listOf("com.a.b"))
        assertTrue(req1.requestId != req2.requestId)
    }
}
