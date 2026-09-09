package com.example.platinumcleaner.domain.inventory

import com.example.platinumcleaner.ui.dashboard.AppInfo
import com.example.platinumcleaner.ui.dashboard.ScanMeasurementStatus
import com.example.platinumcleaner.ui.dashboard.ScanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CacheScannerTest — Unit tests for Sprint V8 Cache Scanner Integrity.
 * Sesuai ai_task.md §13, §14, §15, §59:
 * - Scan states: Scanning, Success, Partial, Empty, Failed, PermissionRequired
 * - Per-package measurement statuses: Measured, Unavailable, Error
 * - Cache aggregate calculation consistency
 * - Partial scan error isolation
 */
class CacheScannerTest {

    @Test
    fun testScanMeasurementStatuses() {
        val measuredApp = AppInfo(
            packageName = "com.example.measured",
            appName = "Measured App",
            cacheBytes = 1024L,
            scanStatus = ScanMeasurementStatus.MEASURED
        )
        assertEquals(ScanMeasurementStatus.MEASURED, measuredApp.scanStatus)

        val unavailApp = AppInfo(
            packageName = "com.example.unavail",
            appName = "Unavailable App",
            cacheBytes = 0L,
            scanStatus = ScanMeasurementStatus.UNAVAILABLE
        )
        assertEquals(ScanMeasurementStatus.UNAVAILABLE, unavailApp.scanStatus)

        val errorApp = AppInfo(
            packageName = "com.example.error",
            appName = "Error App",
            cacheBytes = 0L,
            scanStatus = ScanMeasurementStatus.ERROR
        )
        assertEquals(ScanMeasurementStatus.ERROR, errorApp.scanStatus)
    }

    @Test
    fun testScanStateSuccessAggregation() {
        val apps = listOf(
            AppInfo("com.app1", "App 1", 500L),
            AppInfo("com.app2", "App 2", 1500L),
            AppInfo("com.app3", "App 3", 3000L)
        )
        val totalBytes = apps.sumOf { it.cacheBytes }
        val state = ScanState.Success(apps = apps, totalBytes = totalBytes)

        assertEquals(5000L, state.totalBytes)
        assertEquals(3, state.apps.size)
    }

    @Test
    fun testScanStatePartialPreservesMeasuredData() {
        val measuredApps = listOf(
            AppInfo("com.app1", "App 1", 10_000L)
        )
        val state = ScanState.Partial(
            apps = measuredApps,
            unmeasurableCount = 2,
            totalBytes = 10_000L
        )

        assertEquals(1, state.apps.size)
        assertEquals(2, state.unmeasurableCount)
        assertEquals(10_000L, state.totalBytes)
    }

    @Test
    fun testScanStateEmpty() {
        val state: ScanState = ScanState.Empty
        assertTrue(state is ScanState.Empty)
    }

    @Test
    fun testScanStateFailed() {
        val state = ScanState.Failed("Disk I/O Error")
        assertEquals("Disk I/O Error", state.error)
    }
}
