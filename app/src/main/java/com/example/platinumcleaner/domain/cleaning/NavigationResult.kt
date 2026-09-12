package com.example.platinumcleaner.domain.cleaning

/**
 * NavigationResult — Hasil eksekusi navigasi ke Settings App Info (ai_task.md §53).
 */
enum class NavigationResult {
    /** Halaman App Info berhasil dibuka dan di-resolve */
    APP_INFO_OPENED,

    /** Halaman App Info tidak dapat dibuka (ActivityNotFound / SecurityException) */
    APP_INFO_NOT_OPENED,

    /** Settings menampilkan aplikasi yang salah atau window bukan target */
    WRONG_APP,

    /** Target tidak ditemukan di PackageManager atau sedang dinonaktifkan (ai_task.md §45, §46) */
    TARGET_UNAVAILABLE,

    /** Waktu tunggu navigasi habis */
    TIMEOUT,

    /** Navigasi dibatalkan karena permintaan penghentian pengguna (ai_task.md §8) */
    STOPPED
}
