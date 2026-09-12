package com.example.platinumcleaner.domain.cleaning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InteractiveQueueTest — Unit tests for InteractiveQueue state progression (Opsi 1 / Sprint V10).
 */
class InteractiveQueueTest {

    private val target1 = CleaningTarget("com.telegram", "Telegram", 1_500_000_000L, isEligible = true)
    private val target2 = CleaningTarget("com.google.android.youtube", "YouTube", 800_000_000L, isEligible = true)
    private val target3 = CleaningTarget("com.zhiliaoapp.musically", "TikTok", 600_000_000L, isEligible = true)

    @Test
    fun testInitialQueueState() {
        val queue = InteractiveQueue(targets = listOf(target1, target2, target3))

        assertEquals(3, queue.totalTargets)
        assertEquals(0, queue.currentIndex)
        assertEquals(target1, queue.currentTarget)
        assertEquals(target2, queue.nextTarget)
        assertTrue(queue.hasNext)
        assertFalse(queue.isCompleted)
        assertFalse(queue.isStopped)
        assertEquals(0f, queue.progressFraction, 0.001f)
        assertEquals(2_900_000_000L, queue.totalCacheBytes)
    }

    @Test
    fun testAdvanceProgression() {
        var queue = InteractiveQueue(targets = listOf(target1, target2, target3))

        // Advance 1: Telegram -> YouTube
        queue = queue.advance()
        assertEquals(1, queue.currentIndex)
        assertEquals(target2, queue.currentTarget)
        assertEquals(target3, queue.nextTarget)
        assertTrue(queue.hasNext)
        assertFalse(queue.isCompleted)
        assertTrue(queue.processedPackages.contains("com.telegram"))

        // Advance 2: YouTube -> TikTok
        queue = queue.advance()
        assertEquals(2, queue.currentIndex)
        assertEquals(target3, queue.currentTarget)
        assertNull(queue.nextTarget)
        assertFalse(queue.hasNext)
        assertFalse(queue.isCompleted)
        assertTrue(queue.processedPackages.contains("com.google.android.youtube"))

        // Advance 3: Finish queue
        queue = queue.advance()
        assertEquals(3, queue.currentIndex)
        assertNull(queue.currentTarget)
        assertTrue(queue.isCompleted)
        assertEquals(1f, queue.progressFraction, 0.001f)
        assertTrue(queue.processedPackages.contains("com.zhiliaoapp.musically"))
    }

    @Test
    fun testSkipTarget() {
        var queue = InteractiveQueue(targets = listOf(target1, target2, target3))

        // Skip target 1 (Telegram)
        queue = queue.skip()
        assertEquals(1, queue.currentIndex)
        assertEquals(target2, queue.currentTarget)
        assertTrue(queue.skippedPackages.contains("com.telegram"))
        assertFalse(queue.processedPackages.contains("com.telegram"))

        // Advance target 2 (YouTube)
        queue = queue.advance()
        assertTrue(queue.processedPackages.contains("com.google.android.youtube"))

        // Skip target 3 (TikTok)
        queue = queue.skip()
        assertTrue(queue.isCompleted)
        assertTrue(queue.skippedPackages.contains("com.zhiliaoapp.musically"))
        assertEquals(2, queue.skippedPackages.size)
        assertEquals(1, queue.processedPackages.size)
    }

    @Test
    fun testStopQueue() {
        var queue = InteractiveQueue(targets = listOf(target1, target2, target3))
        queue = queue.advance() // at YouTube
        queue = queue.stop()

        assertTrue(queue.isStopped)
        assertNull(queue.currentTarget)
        assertNull(queue.nextTarget)
    }

    @Test
    fun testEmptyQueueHandling() {
        val queue = InteractiveQueue(targets = emptyList())
        assertEquals(0, queue.totalTargets)
        assertNull(queue.currentTarget)
        assertFalse(queue.hasNext)
        assertEquals(0f, queue.progressFraction, 0.001f)

        val advanced = queue.advance()
        assertEquals(0, advanced.currentIndex)
    }
}
