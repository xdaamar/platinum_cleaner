package com.example.platinumcleaner.domain.verification

import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests untuk VerificationEngine.
 *
 * Menguji:
 * - Per-package classification (VERIFIED_SUCCESS, VERIFIED_PARTIAL, NO_MEASURABLE_CHANGE, UNKNOWN)
 * - System-wide aggregate classification (VERIFIED_SUCCESS, VERIFIED_PARTIAL, NO_MEASURABLE_CHANGE)
 * - Honest wording formatters
 * - Pemisahan NO_MEASURABLE_CHANGE vs FAILED (ai_task.md §38, §39)
 */
class VerificationEngineTest {

    @Test
    fun `classify returns VERIFIED_SUCCESS when cache reduced by 100 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 0L)
        assertEquals(VerificationStatus.VERIFIED_SUCCESS, status)
    }

    @Test
    fun `classify returns VERIFIED_SUCCESS when cache reduced by 90 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 10_000L)
        assertEquals(VerificationStatus.VERIFIED_SUCCESS, status)
    }

    @Test
    fun `classify returns VERIFIED_PARTIAL when cache reduced by 50 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 50_000L)
        assertTrue(status == VerificationStatus.VERIFIED_PARTIAL || status == VerificationStatus.PARTIAL_SUCCESS)
    }

    @Test
    fun `classify returns VERIFIED_PARTIAL when cache reduced by 10 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 90_000L)
        assertTrue(status == VerificationStatus.VERIFIED_PARTIAL || status == VerificationStatus.PARTIAL_SUCCESS)
    }

    @Test
    fun `classify returns NO_MEASURABLE_CHANGE when cache not reduced`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 100_000L)
        assertTrue(status == VerificationStatus.NO_MEASURABLE_CHANGE || status == VerificationStatus.NO_CHANGE)
    }

    @Test
    fun `classify returns NO_MEASURABLE_CHANGE when cache reduced less than 10 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 95_000L)
        assertTrue(status == VerificationStatus.NO_MEASURABLE_CHANGE || status == VerificationStatus.NO_CHANGE)
    }

    @Test
    fun `classify returns NO_MEASURABLE_CHANGE when afterBytes is greater than before`() {
        // Cache bisa naik jika app menulis data baru saat kita baca
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 110_000L)
        assertTrue(status == VerificationStatus.NO_MEASURABLE_CHANGE || status == VerificationStatus.NO_CHANGE)
    }

    @Test
    fun `classify returns UNKNOWN when afterBytes is negative`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = -1L)
        assertEquals(VerificationStatus.UNKNOWN, status)
    }

    @Test
    fun `classify returns UNKNOWN when beforeBytes is zero`() {
        val status = VerificationEngine.classify(beforeBytes = 0L, afterBytes = 0L)
        assertEquals(VerificationStatus.UNKNOWN, status)
    }

    // ===================================================
    // V8 Tests: System-wide aggregate classification
    // ===================================================

    @Test
    fun `classifySystemWide returns VERIFIED_SUCCESS when aggregate cache reduced by 95 percent`() {
        val status = VerificationEngine.classifySystemWide(beforeTotalBytes = 2_000_000_000L, afterTotalBytes = 100_000_000L)
        assertEquals(VerificationStatus.VERIFIED_SUCCESS, status)
    }

    @Test
    fun `classifySystemWide returns VERIFIED_PARTIAL when aggregate cache reduced by 30 percent`() {
        val status = VerificationEngine.classifySystemWide(beforeTotalBytes = 1_000_000_000L, afterTotalBytes = 700_000_000L)
        assertEquals(VerificationStatus.VERIFIED_PARTIAL, status)
    }

    @Test
    fun `classifySystemWide returns NO_MEASURABLE_CHANGE when aggregate cache unchanged`() {
        val status = VerificationEngine.classifySystemWide(beforeTotalBytes = 1_000_000_000L, afterTotalBytes = 1_000_000_000L)
        assertEquals(VerificationStatus.NO_MEASURABLE_CHANGE, status)
        // V8 Rule §38, §39: NO_MEASURABLE_CHANGE is never FAILED
        assertNotEquals(VerificationStatus.FAILED, status)
    }

    @Test
    fun `classifySystemWide returns NO_MEASURABLE_CHANGE when aggregate cache slightly increased`() {
        val status = VerificationEngine.classifySystemWide(beforeTotalBytes = 1_000_000_000L, afterTotalBytes = 1_050_000_000L)
        assertEquals(VerificationStatus.NO_MEASURABLE_CHANGE, status)
    }

    @Test
    fun `classifySystemWide returns UNKNOWN for invalid bytes`() {
        assertEquals(VerificationStatus.UNKNOWN, VerificationEngine.classifySystemWide(0L, 0L))
        assertEquals(VerificationStatus.UNKNOWN, VerificationEngine.classifySystemWide(100L, -1L))
    }

    // ===================================================
    // Formatters Tests
    // ===================================================

    @Test
    fun `formatBytes returns correct GB format`() {
        val result = VerificationEngine.formatBytes(2_147_483_648L) // 2 GB
        assertEquals("2.0 GB", result)
    }

    @Test
    fun `formatBytes returns correct MB format`() {
        val result = VerificationEngine.formatBytes(52_428_800L) // 50 MB
        assertEquals("50 MB", result)
    }

    @Test
    fun `formatBytes returns correct KB format`() {
        val result = VerificationEngine.formatBytes(10_240L) // 10 KB
        assertEquals("10 KB", result)
    }

    @Test
    fun `formatBytes returns NA for negative values`() {
        val result = VerificationEngine.formatBytes(-1L)
        assertEquals("N/A", result)
    }

    @Test
    fun `formatReclaimableEstimate prepends honest wording`() {
        val result = VerificationEngine.formatReclaimableEstimate(52_428_800L)
        assertTrue("Expected honest wording starting with 'Up to', got: $result", result.startsWith("Up to"))
    }

    @Test
    fun `formatReclaimedVerified starts with Reclaimed when bytes positive`() {
        val result = VerificationEngine.formatReclaimedVerified(52_428_800L)
        assertTrue("Expected 'Reclaimed ...', got: $result", result.startsWith("Reclaimed"))
    }

    @Test
    fun `formatReclaimedVerified returns no change message for zero bytes`() {
        val result = VerificationEngine.formatReclaimedVerified(0L)
        assertEquals("No change detected", result)
    }
}
