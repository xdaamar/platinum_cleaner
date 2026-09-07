package com.example.platinumcleaner.service

import android.util.Log
import com.example.platinumcleaner.Constants

/**
 * CleanSessionManager — Singleton sesi cleaning dengan dukungan antrian (batch mode).
 *
 * Sprint 5 Enhancement:
 * - Batch mode: `startBatchSession(packages)` untuk antrian multiple apps
 * - `moveToNext()`: pindah ke app berikutnya dalam antrian, return null jika antrian habis
 * - Double-execution guard tetap dipertahankan dari Sprint 4
 *
 * Sesuai 03_security_protocols.md: Service hanya aktif saat ada sesi eksplisit dari user.
 */
object CleanSessionManager {

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var targetPackageName: String? = null
        private set

    // Sprint 5: Antrian batch
    private val targetQueue: ArrayDeque<String> = ArrayDeque()

    @Volatile
    var currentIndex: Int = 0
        private set

    @Volatile
    var totalApps: Int = 0
        private set

    // ===================================================
    // Single app session (backward compatible)
    // ===================================================

    /**
     * Mulai sesi untuk satu app.
     * @return false jika sesi sebelumnya masih aktif (double-execution guard).
     */
    fun startSession(packageName: String): Boolean {
        if (isActive) {
            Log.w(Constants.TAG_SESSION, "Sesi masih aktif — request diabaikan")
            return false
        }
        targetQueue.clear()
        targetQueue.add(packageName)
        totalApps = 1
        currentIndex = 0
        targetPackageName = packageName
        isActive = true
        Log.d(Constants.TAG_SESSION, "Sesi tunggal dimulai: $packageName")
        return true
    }

    // ===================================================
    // Sprint 5: Batch session
    // ===================================================

    /**
     * Mulai sesi batch untuk beberapa app sekaligus.
     * @param packages Daftar package yang akan dibersihkan secara berurutan.
     * @return false jika sesi sebelumnya masih aktif.
     */
    fun startBatchSession(packages: List<String>): Boolean {
        if (isActive) {
            Log.w(Constants.TAG_SESSION, "Sesi batch masih aktif — request diabaikan")
            return false
        }
        if (packages.isEmpty()) {
            Log.w(Constants.TAG_SESSION, "Daftar package kosong — tidak ada yang dilakukan")
            return false
        }
        targetQueue.clear()
        targetQueue.addAll(packages)
        totalApps = packages.size
        currentIndex = 0
        targetPackageName = targetQueue.first()
        isActive = true
        Log.d(Constants.TAG_SESSION, "Sesi batch dimulai: ${packages.size} app")
        return true
    }

    /**
     * Pindah ke app berikutnya dalam antrian.
     * @return Package name app berikutnya, atau null jika antrian sudah habis.
     */
    fun moveToNext(): String? {
        if (targetQueue.isNotEmpty()) targetQueue.removeFirst()
        currentIndex++

        return if (targetQueue.isNotEmpty()) {
            targetPackageName = targetQueue.first()
            Log.d(Constants.TAG_SESSION, "Pindah ke app ${currentIndex + 1}/$totalApps: $targetPackageName")
            targetPackageName
        } else {
            Log.d(Constants.TAG_SESSION, "Antrian habis — semua app selesai")
            null
        }
    }

    /**
     * Akhiri sesi — bersihkan semua state.
     */
    fun endSession() {
        Log.d(Constants.TAG_SESSION, "Sesi diakhiri. Target terakhir: $targetPackageName")
        targetQueue.clear()
        targetPackageName = null
        currentIndex = 0
        totalApps = 0
        isActive = false
    }
}
