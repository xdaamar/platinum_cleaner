package com.example.platinumcleaner.domain.cleaning

/**
 * CleaningCapability — Enum yang merepresentasikan kemampuan aktual perangkat
 * untuk membersihkan cache, terurut dari yang paling disukai ke paling terbatas.
 *
 * Sesuai ai_task.md §7: CapabilityResolver hanya mendeteksi, tidak mengeksekusi.
 * Sesuai ai_task.md §4: CAPABILITY-BASED, bukan DEVICE-MODEL-BASED.
 */
enum class CleaningCapability(val priority: Int, val displayLabel: String) {

    /**
     * Priority 1 — System-managed cache clearing via ACTION_CLEAR_APP_CACHE.
     * Tersedia pada perangkat tertentu dengan API level yang mendukung.
     * Sistem Android yang menangani konfirmasi.
     */
    SYSTEM_WIDE_CACHE_REQUEST(priority = 1, displayLabel = "System cleanup available"),

    /**
     * Priority 2 — Per-app navigation via ACTION_APPLICATION_DETAILS_SETTINGS.
     * User diarahkan ke halaman detail app, lalu diminta klik Clear Cache sendiri.
     * Selalu tersedia sebagai fallback minimum.
     */
    PER_APP_NAVIGATION(priority = 2, displayLabel = "Assisted cleanup"),

    /**
     * Priority 3 — Accessibility automation (optional, user-enabled).
     * Hanya aktif jika user secara eksplisit mengaktifkan Accessibility Service.
     * Tidak boleh menjadi dependency utama.
     * Sesuai ai_task.md §36: Play Store compatibility — optional, bukan core.
     */
    ACCESSIBILITY_AUTOMATION(priority = 3, displayLabel = "Automation enabled"),

    /**
     * Tidak ada capability yang tersedia — informasikan ke user.
     */
    UNSUPPORTED(priority = 99, displayLabel = "Automatic cleanup unavailable")
}
