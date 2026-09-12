package com.example.platinumcleaner.domain.inventory

import com.example.platinumcleaner.domain.cleaning.CleaningTarget
import com.example.platinumcleaner.ui.dashboard.AppInfo

/**
 * ScanResult — Authoritative single source of truth untuk scan inventory (ai_task.md §39).
 *
 * Mengonsolidasikan semua metadata hasil pemindaian:
 * - rawCount: total package mentah yang terpasang di perangkat
 * - relevantCount: aplikasi relevan (pengguna + launchable apps)
 * - eligibleCount: aplikasi target pembersihan (cache > 0 B dan bukan self-package)
 * - measurableCacheCount: jumlah aplikasi yang memiliki cache terukur (> 0 B)
 * - items: semua aplikasi yang terpindai (termasuk yang cache 0 B, sesuai §6 & §27)
 * - timestamp: waktu pemindaian selesai
 * - errorsCount: jumlah paket yang mengalami pembatasan keamanan / error I/O
 * - totalCacheBytes: total akumulasi cache dari semua aplikasi terukur
 */
data class ScanResult(
    val rawCount: Int = 0,
    val relevantCount: Int = 0,
    val eligibleCount: Int = 0,
    val measurableCacheCount: Int = 0,
    val items: List<AppInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val errorsCount: Int = 0,
    val totalCacheBytes: Long = items.sumOf { it.cacheBytes }
) {
    /**
     * Membangun daftar CleaningTarget yang valid untuk sesi pembersihan.
     * Mengeluarkan aplikasi Platinum Cleaner sendiri (§47) dan aplikasi ber-cache 0 B (§6, §27).
     */
    fun buildCleaningTargets(excludePackageName: String): List<CleaningTarget> {
        return items
            .filter { it.packageName != excludePackageName }
            .filter { it.cacheBytes > 0L }
            .sortedByDescending { it.cacheBytes }
            .map { app ->
                CleaningTarget(
                    packageName = app.packageName,
                    appLabel = app.appName,
                    cacheBytesBefore = app.cacheBytes,
                    isEligible = true
                )
            }
    }
}
