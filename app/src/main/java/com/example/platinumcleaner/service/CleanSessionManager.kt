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
 * ALUR:
 * 1. User tap "Clean" di UI → DashboardViewModel memanggil startSession()
 * 2. PlatinumCleanerService memeriksa isActive sebelum setiap tindakan
 * 3. Saat berhasil/gagal/timeout → endSession() dipanggil → service kembali idle
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
     */
    fun startSession(packageName: String) {
        targetPackageName = packageName
        isActive = true
        Log.d(Constants.TAG_SESSION, "Sesi dimulai untuk: $packageName")
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
