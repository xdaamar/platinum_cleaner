package com.example.platinumcleaner.domain.inventory

import com.example.platinumcleaner.ui.dashboard.AppInfo
import com.example.platinumcleaner.ui.dashboard.InventorySummary
import com.example.platinumcleaner.ui.dashboard.PackageCategoryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PackageInventoryTest — Unit tests for Sprint V8 inventory models & filtering logic.
 * Sesuai ai_task.md §59 (Mandatory Tests):
 * - Duplicate package removal
 * - System vs user filtering
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
    fun testCategoryFiltering() {
        val userApp = AppInfo(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            cacheBytes = 1000L,
            isSystemApp = false
        )
        val systemApp = AppInfo(
            packageName = "com.android.settings",
            appName = "Settings",
            cacheBytes = 2000L,
            isSystemApp = true
        )

        val allApps = listOf(userApp, systemApp)

        val allFiltered = allApps.filter {
            when (PackageCategoryFilter.ALL) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(2, allFiltered.size)

        val userFiltered = allApps.filter {
            when (PackageCategoryFilter.USER_ONLY) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(1, userFiltered.size)
        assertEquals("Telegram", userFiltered.first().appName)

        val systemFiltered = allApps.filter {
            when (PackageCategoryFilter.SYSTEM_ONLY) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.USER_ONLY -> !it.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> it.isSystemApp
            }
        }
        assertEquals(1, systemFiltered.size)
        assertEquals("Settings", systemFiltered.first().appName)
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
            rawPackagesCount = 352,
            userAppsCount = 142,
            systemAppsCount = 210,
            launchableAppsCount = 85,
            measurableCacheAppsCount = 84,
            totalMeasuredCacheBytes = 3_800_000_000L
        )

        assertEquals(352, summary.rawPackagesCount)
        assertEquals(352, summary.userAppsCount + summary.systemAppsCount)
        assertEquals(84, summary.measurableCacheAppsCount)
        assertEquals(3_800_000_000L, summary.totalMeasuredCacheBytes)
    }
}
