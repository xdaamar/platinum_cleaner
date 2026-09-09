package com.example.platinumcleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.ui.dashboard.AppInfo
import com.example.platinumcleaner.ui.dashboard.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

import com.example.platinumcleaner.ui.dashboard.InventorySummary

/**
 * Repository layer untuk mengambil data aplikasi dan cache dari perangkat.
 *
 * Sesuai 04_performance_budget.md: Semua operasi I/O WAJIB di Dispatchers.IO.
 * Sesuai 03_security_protocols.md: Tidak ada network call. 100% on-device.
 * Sesuai 02_coding_standards.md: Single Responsibility, No Magic Numbers.
 * Sesuai Sprint V8 PRD Override: Real package inventory, deduplication, categorization.
 */
class AppCleanerRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /** Authoritative inventory summary dari hasil scan terakhir */
    var lastInventorySummary: InventorySummary = InventorySummary()
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

            // V8 FIX: Deduplikasi package berdasarkan packageName
            val uniquePackages = LinkedHashMap<String, android.content.pm.PackageInfo>()
            for (pkg in rawPackages) {
                if (uniquePackages.containsKey(pkg.packageName)) {
                    Log.d(Constants.TAG_REPO, "[DUPLICATE_PACKAGE] package=${pkg.packageName}")
                } else {
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

            for (packageInfo in uniquePackages.values) {
                try {
                    val isSystemApp = (packageInfo.applicationInfo.flags and
                            android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val isLaunchable = packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                    val isEnabled = packageInfo.applicationInfo.enabled
                    val uid = packageInfo.applicationInfo.uid

                    // Ambil ukuran cache via StorageStatsManager
                    val storageStats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageInfo.packageName,
                        UserHandle.getUserHandleForUid(uid)
                    )

                    val cacheBytes = storageStats.cacheBytes

                    // Hanya masukkan app yang punya cache (> 0 bytes)
                    if (cacheBytes > 0L) {
                        val appName = try {
                            packageManager.getApplicationLabel(
                                packageInfo.applicationInfo
                            ).toString()
                        } catch (e: Exception) {
                            packageInfo.packageName
                        }

                        appList.add(
                            AppInfo(
                                packageName = packageInfo.packageName,
                                appName = appName,
                                cacheBytes = cacheBytes,
                                isSystemApp = isSystemApp,
                                isLaunchable = isLaunchable,
                                isEnabled = isEnabled,
                                uid = uid
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Beberapa package system mungkin tidak bisa di-query — skip saja
                    Log.d(Constants.TAG_REPO, "Skip package ${packageInfo.packageName}: ${e.message}")
                }
            }

            // Urutkan berdasarkan ukuran cache terbesar
            val sortedList = appList.sortedByDescending { it.cacheBytes }
            val totalMeasuredCache = sortedList.sumOf { it.cacheBytes }

            lastInventorySummary = InventorySummary(
                rawPackagesCount = rawPackages.size,
                userAppsCount = userAppsCount,
                systemAppsCount = systemAppsCount,
                launchableAppsCount = launchableAppsCount,
                measurableCacheAppsCount = sortedList.size,
                totalMeasuredCacheBytes = totalMeasuredCache
            )

            Log.d(
                Constants.TAG_REPO,
                "[INVENTORY] rawPackages=${rawPackages.size} unique=${uniquePackages.size} userApps=$userAppsCount systemApps=$systemAppsCount launchableApps=$launchableAppsCount measurableCacheApps=${sortedList.size} totalCacheBytes=$totalMeasuredCache"
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
     * Menghitung progress gauge (0f - 1f) berdasarkan total cache vs storage total.
     */
    fun calculateGaugeProgress(apps: List<AppInfo>): Float {
        val totalCacheBytes = apps.sumOf { it.cacheBytes }
        // Representasikan sebagai proporsi dari 2GB sebagai reference point
        val referenceBytes = 2L * Constants.BYTES_PER_GB
        return (totalCacheBytes.toFloat() / referenceBytes.toFloat()).coerceIn(0.05f, 0.99f)
    }
}
