package com.example.platinumcleaner.service

import android.util.Log
import com.example.platinumcleaner.Constants

/**
 * CleanSessionManager — Singleton yang mengontrol state sesi pembersihan aktif.
 *
 * TUJUAN KEAMANAN:
 * Tanpa manager ini, AccessibilityService bisa "nyasar" dan berinteraksi dengan
 * UI Settings bahkan saat tidak ada perintah dari user. CleanSessionManager
 * memastikan service HANYA bekerja saat ada sesi yang secara eksplisit dipicu
 * oleh user melalui tombol di UI.
 *
 * Sprint 4 Enhancement:
 * - Guard double-execution: startSession() menolak request baru jika sesi masih aktif.
 *   Mencegah race condition jika user spam tombol "Clean".
 *
 * Sesuai 03_security_protocols.md: Mencegah eksekusi liar dari service.
 */
object CleanSessionManager {

    /** True hanya saat ada sesi cleaning yang aktif dipicu oleh user. */
    @Volatile
    var isActive: Boolean = false
        private set

    /** Package name dari app yang sedang dibersihkan. Null saat tidak ada sesi aktif. */
    @Volatile
    var targetPackageName: String? = null
        private set

    /**
     * Mulai sesi baru. Hanya boleh dipanggil dari DashboardViewModel saat user
     * secara eksplisit menekan tombol clean.
     *
     * @return true jika sesi berhasil dimulai, false jika sesi sebelumnya masih aktif (double-execution guard).
     */
    fun startSession(packageName: String): Boolean {
        // GUARD: Tolak request baru jika sesi sebelumnya masih berjalan.
        // Mencegah race condition / spam click pada tombol Clean.
        if (isActive) {
            Log.w(Constants.TAG_SESSION, "Sesi masih aktif untuk $targetPackageName — request baru diabaikan")
            return false
        }
        targetPackageName = packageName
        isActive = true
        Log.d(Constants.TAG_SESSION, "Sesi dimulai untuk: $packageName")
        return true
    }

    /**
     * Akhiri sesi — dipanggil setelah berhasil, gagal, atau timeout.
     */
    fun endSession() {
        Log.d(Constants.TAG_SESSION, "Sesi diakhiri. Target sebelumnya: $targetPackageName")
        targetPackageName = null
        isActive = false
    }
}
