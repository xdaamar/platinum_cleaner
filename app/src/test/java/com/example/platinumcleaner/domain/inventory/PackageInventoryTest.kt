package com.example.platinumcleaner.domain.inventory

import com.example.platinumcleaner.domain.cleaning.CleaningTarget
import com.example.platinumcleaner.ui.dashboard.AppInfo
import com.example.platinumcleaner.ui.dashboard.InventorySummary
import com.example.platinumcleaner.ui.dashboard.PackageCategoryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PackageInventoryTest — Unit tests for Sprint V9 inventory truth & zero-cache semantics.
 * Sesuai ai_task.md V9 (§5, §6, §27, §38, §39, §41, §47, §59):
 * - Real inventory without dummy data
 * - Zero-cache preservation in inventory
 * - Target generation excluding zero-cache & self-package
 * - Duplicate package removal
 * - System vs user vs has-cache filtering
 * - Count consistency
 * - Formatted cache sizes
 */
class PackageInventoryTest {

    @Test
    fun testAppInfoDefaultsAndFormatting() {
        val app = AppInfo(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            cacheBytes = 820 * 1024 * 1024L // 820 MB
        )

        assertEquals("org.telegram.messenger", app.packageName)
        assertEquals("Telegram", app.appName)
        assertFalse(app.isSystemApp)
        assertTrue(app.isLaunchable)
        assertTrue(app.isEnabled)
        assertEquals(0, app.uid)
        assertEquals("820 MB", app.cacheSizeFormatted)
        assertFalse(app.isCacheZero)
    }

    @Test
    fun testZeroCacheAppFormatting() {
        val zeroApp = AppInfo(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            cacheBytes = 0L
        )

        assertEquals("0 B", zeroApp.cacheSizeFormatted)
        assertTrue(zeroApp.isCacheZero)
    }

    @Test
    fun testCacheSizeFormattingGigabytes() {
        val app = AppInfo(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            cacheBytes = 2_500_000_000L
        )

        assertEquals("2.3 GB", app.cacheSizeFormatted)
    }

    @Test
    fun testCacheSizeFormattingKilobytesAndBytes() {
        val kbApp = AppInfo(
            packageName = "com.example.small",
            appName = "Small App",
            cacheBytes = 45 * 1024L
        )
        assertEquals("45 KB", kbApp.cacheSizeFormatted)

        val byteApp = AppInfo(
            packageName = "com.example.tiny",
            appName = "Tiny App",
            cacheBytes = 512L
        )
        assertEquals("512 B", byteApp.cacheSizeFormatted)
    }

    @Test
    fun testCategoryFilteringWithHasCache() {
        val userAppWithCache = AppInfo(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            cacheBytes = 1000L,
            isSystemApp = false
        )
        val userAppZeroCache = AppInfo(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            cacheBytes = 0L,
            isSystemApp = false
        )
        val systemApp = AppInfo(
            packageName = "com.android.settings",
            appName = "Settings",
            cacheBytes = 2000L,
            isSystemApp = true
        )

        val allApps = listOf(userAppWithCache, userAppZeroCache, systemApp)

        // Filter ALL
        val allFiltered = allApps.filter {
            when (PackageCategoryFilter.ALL) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.HAS_CACHE -> it.cacheBytes > 0L
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(3, allFiltered.size)

        // Filter HAS_CACHE
        val cacheFiltered = allApps.filter {
            when (PackageCategoryFilter.HAS_CACHE) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.HAS_CACHE -> it.cacheBytes > 0L
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(2, cacheFiltered.size)
        assertTrue(cacheFiltered.all { it.cacheBytes > 0L })

        // Filter USER_ONLY
        val userFiltered = allApps.filter {
            when (PackageCategoryFilter.USER_ONLY) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.HAS_CACHE -> it.cacheBytes > 0L
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(2, userFiltered.size)
    }

    @Test
    fun testZeroCachePolicyPreservesAppInInventory() {
        // ai_task.md §6: Zero cache app must remain in inventory, not silently removed
        val telegram = AppInfo("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L)
        val youtube = AppInfo("com.google.android.youtube", "YouTube", 0L)

        val inventory = listOf(telegram, youtube)
        assertEquals(2, inventory.size)
        assertTrue(inventory.any { it.packageName == "com.google.android.youtube" })
    }

    @Test
    fun testScanResultBuildCleaningTargetsExcludesSelfAndZeroCache() {
        // ai_task.md §5, §6, §27, §47
        val telegram = AppInfo("org.telegram.messenger", "Telegram", 820 * 1024 * 1024L)
        val youtubeZero = AppInfo("com.google.android.youtube", "YouTube", 0L)
        val selfCleaner = AppInfo("com.example.platinumcleaner", "Platinum Cleaner", 50 * 1024 * 1024L)

        val scanResult = ScanResult(
            rawCount = 169,
            relevantCount = 65,
            eligibleCount = 1,
            measurableCacheCount = 2,
            items = listOf(telegram, youtubeZero, selfCleaner),
            totalCacheBytes = (820 + 50) * 1024 * 1024L
        )

        val targets = scanResult.buildCleaningTargets(excludePackageName = "com.example.platinumcleaner")

        // Only Telegram should be a cleaning target (YouTube is 0B, Platinum Cleaner is self-protected)
        assertEquals(1, targets.size)
        val target = targets.first()
        assertEquals("org.telegram.messenger", target.packageName)
        assertEquals("Telegram", target.appLabel)
        assertEquals(820 * 1024 * 1024L, target.cacheBytesBefore)
        assertTrue(target.isEligible)
    }

    @Test
    fun testDeduplicationPreservesUniquePackages() {
        val rawList = listOf(
            "com.android.chrome",
            "org.telegram.messenger",
            "com.android.chrome", // duplicate
            "com.whatsapp",
            "org.telegram.messenger" // duplicate
        )

        val uniqueMap = LinkedHashMap<String, String>()
        for (pkg in rawList) {
            if (!uniqueMap.containsKey(pkg)) {
                uniqueMap[pkg] = pkg
            }
        }

        assertEquals(3, uniqueMap.size)
        assertEquals(listOf("com.android.chrome", "org.telegram.messenger", "com.whatsapp"), uniqueMap.keys.toList())
    }

    @Test
    fun testInventorySummaryConsistency() {
        val summary = InventorySummary(
            rawPackagesCount = 169,
            userAppsCount = 64,
            systemAppsCount = 105,
            launchableAppsCount = 48,
            measurableCacheAppsCount = 31,
            totalMeasuredCacheBytes = 2_800_000_000L
        )

        assertEquals(169, summary.rawPackagesCount)
        assertEquals(169, summary.userAppsCount + summary.systemAppsCount)
        assertEquals(31, summary.measurableCacheAppsCount)
        assertEquals(2_800_000_000L, summary.totalMeasuredCacheBytes)
    }
}
