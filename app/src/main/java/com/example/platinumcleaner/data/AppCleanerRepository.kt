package com.example.platinumcleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.inventory.ScanResult
import com.example.platinumcleaner.ui.dashboard.AppInfo
import com.example.platinumcleaner.ui.dashboard.InventorySummary
import com.example.platinumcleaner.ui.dashboard.ScanMeasurementStatus
import com.example.platinumcleaner.ui.dashboard.ScanState
import com.example.platinumcleaner.ui.dashboard.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Repository layer untuk mengambil data aplikasi dan cache dari perangkat.
 *
 * Sesuai 04_performance_budget.md: Semua operasi I/O WAJIB di Dispatchers.IO.
 * Sesuai 03_security_protocols.md: Tidak ada network call. 100% on-device.
 * Sesuai 02_coding_standards.md: Single Responsibility, No Magic Numbers.
 * Sesuai ai_task.md V9 (§6, §27, §38, §39, §43, §44, §47):
 * - Real PackageManager inventory & deduplikasi
 * - Authoritative ScanResult sebagai Single Source of Truth
 * - Zero Cache Policy: Aplikasi dengan cache 0 B TETAP ADA di inventory, TIDAK dihapus diam-diam!
 * - Isolasi error per-package agar 1 error tidak merusak seluruh hasil scan.
 */
class AppCleanerRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /** Authoritative inventory summary dari hasil scan terakhir */
    var lastInventorySummary: InventorySummary = InventorySummary()
        private set

    /** Authoritative ScanResult dari hasil scan terakhir (ai_task.md §39) */
    var lastScanResult: ScanResult = ScanResult()
        private set

    /**
     * Mengambil daftar aplikasi yang terinstall beserta ukuran cache masing-masing.
     * Menggunakan StorageStatsManager (API 26+) untuk akurasi ukuran cache.
     *
     * @return Flow<UiState<List<AppInfo>>> yang bisa diobservasi secara reaktif oleh ViewModel.
     */
    fun getInstalledAppsWithCache(): Flow<UiState<List<AppInfo>>> = flow {
        emit(UiState.Loading)

        try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

            // Ambil daftar semua package yang terinstall untuk user
            @Suppress("DEPRECATION")
            val rawPackages = packageManager.getInstalledPackages(0)

            // Deduplikasi package berdasarkan packageName
            val uniquePackages = LinkedHashMap<String, android.content.pm.PackageInfo>()
            for (pkg in rawPackages) {
                if (!uniquePackages.containsKey(pkg.packageName)) {
                    uniquePackages[pkg.packageName] = pkg
                }
            }

            var userAppsCount = 0
            var systemAppsCount = 0
            var launchableAppsCount = 0

            for (packageInfo in uniquePackages.values) {
                val isSystem = (packageInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) systemAppsCount++ else userAppsCount++

                val isLaunchable = packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                if (isLaunchable) launchableAppsCount++
            }

            val appList = mutableListOf<AppInfo>()
            var measuredCount = 0
            var unmeasurableCount = 0
            var errorCount = 0

            for (packageInfo in uniquePackages.values) {
                val isSystemApp = (packageInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isLaunchable = packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                val isEnabled = packageInfo.applicationInfo.enabled
                val uid = packageInfo.applicationInfo.uid

                val appName = try {
                    packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                } catch (e: Exception) {
                    packageInfo.packageName
                }

                try {
                    // Ambil ukuran cache via StorageStatsManager
                    val storageStats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageInfo.packageName,
                        UserHandle.getUserHandleForUid(uid)
                    )

                    val cacheBytes = storageStats.cacheBytes
                    measuredCount++

                    // V9 SPRINT FIX (§6, §27): Sertakan SEMUA aplikasi, termasuk yang cacheBytes == 0L!
                    // Aplikasi dengan cache 0 B tetap tampil di list inventory.
                    appList.add(
                        AppInfo(
                            packageName = packageInfo.packageName,
                            appName = appName,
                            cacheBytes = cacheBytes,
                            isSystemApp = isSystemApp,
                            isLaunchable = isLaunchable,
                            isEnabled = isEnabled,
                            uid = uid,
                            scanStatus = ScanMeasurementStatus.MEASURED
                        )
                    )
                } catch (e: SecurityException) {
                    // Package diblokir oleh kebijakan keamanan atau profil lain
                    unmeasurableCount++
                    Log.d(Constants.TAG_REPO, "[SCANNER] Security restricted package ${packageInfo.packageName}: ${e.message}")
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    // Package di-uninstall saat scan sedang berjalan
                    unmeasurableCount++
                    Log.d(Constants.TAG_REPO, "[SCANNER] Package not found during query ${packageInfo.packageName}: ${e.message}")
                } catch (e: java.io.IOException) {
                    // Transient I/O error
                    errorCount++
                    Log.w(Constants.TAG_REPO, "[SCANNER] Storage IO error for ${packageInfo.packageName}: ${e.message}")
                } catch (e: Exception) {
                    // Error umum lainnya
                    errorCount++
                    Log.d(Constants.TAG_REPO, "[SCANNER] Error querying ${packageInfo.packageName}: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            // Urutkan: cache terbesar dulu, lalu nama aplikasi A-Z
            val sortedList = appList.sortedWith(
                compareByDescending<AppInfo> { it.cacheBytes }
                    .thenBy { it.appName.lowercase() }
            )

            val measurableCacheApps = sortedList.filter { it.cacheBytes > 0L }
            val totalMeasuredCache = measurableCacheApps.sumOf { it.cacheBytes }
            val ourPackage = context.packageName
            val eligibleTargets = measurableCacheApps.filter { it.packageName != ourPackage }

            // Authoritative ScanResult (§39)
            val scanResult = ScanResult(
                rawCount = rawPackages.size,
                relevantCount = userAppsCount + launchableAppsCount,
                eligibleCount = eligibleTargets.size,
                measurableCacheCount = measurableCacheApps.size,
                items = sortedList,
                timestamp = System.currentTimeMillis(),
                errorsCount = unmeasurableCount + errorCount,
                totalCacheBytes = totalMeasuredCache
            )
            lastScanResult = scanResult

            lastInventorySummary = InventorySummary(
                rawPackagesCount = rawPackages.size,
                userAppsCount = userAppsCount,
                systemAppsCount = systemAppsCount,
                launchableAppsCount = launchableAppsCount,
                measurableCacheAppsCount = measurableCacheApps.size,
                unmeasurablePackagesCount = unmeasurableCount,
                errorPackagesCount = errorCount,
                totalMeasuredCacheBytes = totalMeasuredCache
            )

            Log.d(
                Constants.TAG_REPO,
                "[SCANNER] rawPackages=${rawPackages.size} unique=${uniquePackages.size} measured=$measuredCount cacheApps=${measurableCacheApps.size} zeroCacheApps=${sortedList.size - measurableCacheApps.size} totalCacheBytes=$totalMeasuredCache"
            )
            Log.d(
                Constants.TAG_REPO,
                "[INVENTORY] relevant=${scanResult.relevantCount} eligibleTargets=${scanResult.eligibleCount} errors=${scanResult.errorsCount}"
            )

            emit(UiState.Success(sortedList))

        } catch (e: SecurityException) {
            Log.e(Constants.TAG_REPO, "Izin Usage Access belum diberikan: ${e.message}")
            emit(UiState.PermissionRequired)
        } catch (e: Exception) {
            Log.e(Constants.TAG_REPO, "Error membaca data cache: ${e.message}")
            emit(UiState.Error("Gagal membaca data cache: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO) // WAJIB: semua operasi I/O di background thread

    /**
     * Flow ScanState eksplisit sesuai ai_task.md §14 & §39.
     * Mengembalikan status SCANNING -> SUCCESS/PARTIAL/EMPTY/FAILED/PERMISSION_REQUIRED.
     */
    fun scanCacheStats(): Flow<ScanState> = flow {
        emit(ScanState.Scanning)

        try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

            @Suppress("DEPRECATION")
            val rawPackages = packageManager.getInstalledPackages(0)

            val uniquePackages = LinkedHashMap<String, android.content.pm.PackageInfo>()
            for (pkg in rawPackages) {
                if (!uniquePackages.containsKey(pkg.packageName)) {
                    uniquePackages[pkg.packageName] = pkg
                }
            }

            val appList = mutableListOf<AppInfo>()
            var unmeasurableCount = 0
            var errorCount = 0

            for (packageInfo in uniquePackages.values) {
                val isSystemApp = (packageInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isLaunchable = packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                val isEnabled = packageInfo.applicationInfo.enabled
                val uid = packageInfo.applicationInfo.uid

                val appName = try {
                    packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                } catch (e: Exception) {
                    packageInfo.packageName
                }

                try {
                    val storageStats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageInfo.packageName,
                        UserHandle.getUserHandleForUid(uid)
                    )

                    val cacheBytes = storageStats.cacheBytes
                    appList.add(
                        AppInfo(
                            packageName = packageInfo.packageName,
                            appName = appName,
                            cacheBytes = cacheBytes,
                            isSystemApp = isSystemApp,
                            isLaunchable = isLaunchable,
                            isEnabled = isEnabled,
                            uid = uid,
                            scanStatus = ScanMeasurementStatus.MEASURED
                        )
                    )
                } catch (e: SecurityException) {
                    unmeasurableCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }

            val sortedList = appList.sortedWith(
                compareByDescending<AppInfo> { it.cacheBytes }
                    .thenBy { it.appName.lowercase() }
            )
            val totalBytes = sortedList.filter { it.cacheBytes > 0L }.sumOf { it.cacheBytes }

            when {
                sortedList.isEmpty() -> emit(ScanState.Empty)
                unmeasurableCount > 0 || errorCount > 0 ->
                    emit(ScanState.Partial(sortedList, unmeasurableCount + errorCount, totalBytes))
                else -> emit(ScanState.Success(sortedList, totalBytes))
            }
        } catch (e: SecurityException) {
            emit(ScanState.PermissionRequired)
        } catch (e: Exception) {
            emit(ScanState.Failed("Gagal memindai cache: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Menghitung total cache dari list AppInfo dalam format yang mudah dibaca manusia.
     */
    fun calculateTotalCacheFormatted(apps: List<AppInfo>): String {
        val totalBytes = apps.sumOf { it.cacheBytes }
        return when {
            totalBytes >= Constants.BYTES_PER_GB ->
                String.format("%.1f", totalBytes / Constants.BYTES_PER_GB.toDouble())
            totalBytes >= Constants.BYTES_PER_MB ->
                String.format("%.0f", totalBytes / Constants.BYTES_PER_MB.toDouble())
            totalBytes >= Constants.BYTES_PER_KB ->
                String.format("%.0f", totalBytes / Constants.BYTES_PER_KB.toDouble())
            else -> "$totalBytes"
        }
    }

    /**
     * Menghitung unit string untuk total cache (GB, MB, KB, B).
     */
    fun calculateTotalCacheUnit(apps: List<AppInfo>): String {
        val totalBytes = apps.sumOf { it.cacheBytes }
        return when {
            totalBytes >= Constants.BYTES_PER_GB -> "Gigabytes"
            totalBytes >= Constants.BYTES_PER_MB -> "Megabytes"
            totalBytes >= Constants.BYTES_PER_KB -> "Kilobytes"
            else -> "Bytes"
        }
    }

    /**
     * Menghitung progress gauge (0f - 1f) berdasarkan total cache vs storage reference.
     */
    fun calculateGaugeProgress(apps: List<AppInfo>): Float {
        val totalCacheBytes = apps.sumOf { it.cacheBytes }
        // Representasikan sebagai proporsi dari 2GB sebagai reference point
        val referenceBytes = 2L * Constants.BYTES_PER_GB
        return (totalCacheBytes.toFloat() / referenceBytes.toFloat()).coerceIn(0.05f, 0.99f)
    }
}
