package com.example.platinumcleaner.domain.verification

import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests untuk VerificationEngine.classify().
 *
 * Tidak membutuhkan Android context — murni logika domain.
 * Sesuai ai_task.md §28: unit test verification calculation.
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
    fun `classify returns PARTIAL_SUCCESS when cache reduced by 50 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 50_000L)
        assertEquals(VerificationStatus.PARTIAL_SUCCESS, status)
    }

    @Test
    fun `classify returns PARTIAL_SUCCESS when cache reduced by 10 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 90_000L)
        assertEquals(VerificationStatus.PARTIAL_SUCCESS, status)
    }

    @Test
    fun `classify returns NO_CHANGE when cache not reduced`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 100_000L)
        assertEquals(VerificationStatus.NO_CHANGE, status)
    }

    @Test
    fun `classify returns NO_CHANGE when cache reduced less than 10 percent`() {
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 95_000L)
        assertEquals(VerificationStatus.NO_CHANGE, status)
    }

    @Test
    fun `classify returns NO_CHANGE when afterBytes is greater than before`() {
        // Cache bisa naik jika app menulis data baru saat kita baca
        val status = VerificationEngine.classify(beforeBytes = 100_000L, afterBytes = 110_000L)
        assertEquals(VerificationStatus.NO_CHANGE, status)
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
        assert(result.startsWith("Up to")) {
            "Expected honest wording starting with 'Up to', got: $result"
        }
    }

    @Test
    fun `formatReclaimedVerified starts with Reclaimed when bytes positive`() {
        val result = VerificationEngine.formatReclaimedVerified(52_428_800L)
        assert(result.startsWith("Reclaimed")) {
            "Expected 'Reclaimed ...', got: $result"
        }
    }

    @Test
    fun `formatReclaimedVerified returns no change message for zero bytes`() {
        val result = VerificationEngine.formatReclaimedVerified(0L)
        assertEquals("No change detected", result)
    }
}
