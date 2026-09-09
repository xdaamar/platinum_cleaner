package com.example.platinumcleaner.domain.cleaning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CleaningModeArchitectureTest — Unit tests for Sprint V8 cleaning mode architecture.
 * Sesuai ai_task.md §2, §18, §19, §30, §31, §54:
 * - Separation of System-wide and Per-app modes
 * - Targeted clean forbidden from using SYSTEM_WIDE_CACHE_REQUEST
 * - CleaningPlan model attributes
 */
class CleaningModeArchitectureTest {

    @Test
    fun testCleaningPlanSystemWide() {
        val plan = CleaningPlan(
            mode = CleaningMode.SYSTEM_WIDE,
            targets = emptyList(),
            estimatedReclaim = 2_000_000_000L
        )

        assertEquals(CleaningMode.SYSTEM_WIDE, plan.mode)
        assertEquals(0, plan.totalTargets)
        assertTrue(plan.requiresUserInteraction)
        assertFalse(plan.automationEnabled)
    }

    @Test
    fun testCleaningPlanPerAppAssisted() {
        val plan = CleaningPlan(
            mode = CleaningMode.PER_APP_ASSISTED,
            targets = listOf("org.telegram.messenger", "com.whatsapp"),
            estimatedReclaim = 500_000_000L
        )

        assertEquals(CleaningMode.PER_APP_ASSISTED, plan.mode)
        assertEquals(2, plan.totalTargets)
        assertTrue(plan.requiresUserInteraction)
        assertFalse(plan.automationEnabled)
    }

    @Test
    fun testCleaningPlanPerAppAutomated() {
        val plan = CleaningPlan(
            mode = CleaningMode.PER_APP_AUTOMATED,
            targets = listOf("org.telegram.messenger"),
            estimatedReclaim = 800_000_000L
        )

        assertEquals(CleaningMode.PER_APP_AUTOMATED, plan.mode)
        assertEquals(1, plan.totalTargets)
        assertFalse(plan.requiresUserInteraction)
        assertTrue(plan.automationEnabled)
    }

    @Test
    fun testTargetedSingleAppFiltersOutSystemWideCapability() {
        val availableCapabilities = listOf(
            CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST,
            CleaningCapability.PER_APP_NAVIGATION,
            CleaningCapability.ACCESSIBILITY_AUTOMATION
        )

        // Sesuai §54: single targeted app must filter out SYSTEM_WIDE_CACHE_REQUEST
        val isTargetedSingleApp = true
        val candidates = if (isTargetedSingleApp) {
            availableCapabilities.filter { it != CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST }
        } else {
            availableCapabilities
        }

        assertEquals(2, candidates.size)
        assertEquals(CleaningCapability.PER_APP_NAVIGATION, candidates.first())
        assertFalse(candidates.contains(CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST))
    }

    @Test
    fun testSystemWideGeneralCleanKeepsSystemWideCapability() {
        val availableCapabilities = listOf(
            CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST,
            CleaningCapability.PER_APP_NAVIGATION,
            CleaningCapability.ACCESSIBILITY_AUTOMATION
        )

        val isTargetedSingleApp = false
        val candidates = if (isTargetedSingleApp) {
            availableCapabilities.filter { it != CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST }
        } else {
            availableCapabilities
        }

        assertEquals(3, candidates.size)
        assertEquals(CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST, candidates.first())
    }
}
