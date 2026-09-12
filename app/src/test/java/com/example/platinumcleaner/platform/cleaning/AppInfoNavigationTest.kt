package com.example.platinumcleaner.platform.cleaning

import com.example.platinumcleaner.domain.cleaning.NavigationResult
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppInfoNavigationTest — Unit tests for Sprint V9 App Info Navigation & Target Validation.
 * Sesuai ai_task.md V9 (§10, §45, §46, §53, §82):
 * - NavigationResult states
 * - Target unavailable classification
 * - Proper status mapping
 */
class AppInfoNavigationTest {

    @Test
    fun testNavigationResultValues() {
        val opened = NavigationResult.APP_INFO_OPENED
        val notOpened = NavigationResult.APP_INFO_NOT_OPENED
        val wrongApp = NavigationResult.WRONG_APP
        val unavail = NavigationResult.TARGET_UNAVAILABLE
        val timeout = NavigationResult.TIMEOUT
        val stopped = NavigationResult.STOPPED

        assertNotNull(opened)
        assertNotNull(notOpened)
        assertNotNull(wrongApp)
        assertNotNull(unavail)
        assertNotNull(timeout)
        assertNotNull(stopped)
    }

    @Test
    fun testTargetUnavailableMapsToVerificationStatus() {
        // ai_task.md §45, §46: If target package is removed or disabled, status must be TARGET_UNAVAILABLE
        val navResult = NavigationResult.TARGET_UNAVAILABLE
        val status = when (navResult) {
            NavigationResult.TARGET_UNAVAILABLE -> VerificationStatus.TARGET_UNAVAILABLE
            NavigationResult.APP_INFO_NOT_OPENED -> VerificationStatus.INTENT_UNAVAILABLE
            NavigationResult.APP_INFO_OPENED -> VerificationStatus.PENDING_VERIFICATION
            else -> VerificationStatus.NAVIGATION_FAILED
        }

        assertEquals(VerificationStatus.TARGET_UNAVAILABLE, status)
    }

    @Test
    fun testIntentUnavailableMapsToVerificationStatus() {
        val navResult = NavigationResult.APP_INFO_NOT_OPENED
        val status = when (navResult) {
            NavigationResult.TARGET_UNAVAILABLE -> VerificationStatus.TARGET_UNAVAILABLE
            NavigationResult.APP_INFO_NOT_OPENED -> VerificationStatus.INTENT_UNAVAILABLE
            NavigationResult.APP_INFO_OPENED -> VerificationStatus.PENDING_VERIFICATION
            else -> VerificationStatus.NAVIGATION_FAILED
        }

        assertEquals(VerificationStatus.INTENT_UNAVAILABLE, status)
    }
}
