package com.example.platinumcleaner

/**
 * Central constants to avoid magic strings/numbers across the codebase.
 * Sesuai 02_coding_standards.md: No Magic Numbers/Strings.
 */
object Constants {
    // === SECURITY: AccessibilityService Scope Limitation ===
    // HUKUM BESI: Service HANYA boleh berinteraksi dengan package ini (03_security_protocols.md)
    const val SETTINGS_PACKAGE = "com.android.settings"

    // Package kita sendiri — digunakan untuk mendeteksi user menekan Back dari Settings
    const val OUR_PACKAGE_NAME = "com.example.platinumcleaner"

    // === LOGGING TAGS ===
    const val TAG_SERVICE = "PlatinumCleanerService"
    const val TAG_REPO = "AppCleanerRepository"
    const val TAG_VIEWMODEL = "DashboardViewModel"
    const val TAG_SESSION = "CleanSessionManager"

    // === CACHE FORMATTING ===
    const val BYTES_PER_KB = 1_024L
    const val BYTES_PER_MB = 1_048_576L
    const val BYTES_PER_GB = 1_073_741_824L

    // === UI ===
    const val MAX_DASHBOARD_APP_ITEMS = 5

    // === AUTO-CLEAN NAVIGATION LABELS (Multi-OEM) ===
    // Mencakup variasi teks dari AOSP (Pixel), Samsung One UI, Xiaomi MIUI, dll.
    // Sesuai 02_coding_standards.md: No Magic Strings.
    val STORAGE_LABELS = listOf(
        "Storage",
        "Penyimpanan",
        "Storage & cache",
        "Storage and cache",
        "Penyimpanan & cache",
        "Penggunaan penyimpanan"
    )

    val CLEAR_CACHE_LABELS = listOf(
        "Clear cache",
        "Hapus cache",
        "Clear Cache",
        "CLEAR CACHE",
        "Bersihkan cache",
        "Delete cache"
    )

    val CONFIRM_LABELS = listOf(
        "OK",
        "Yes",
        "Ya",
        "Confirm",
        "Konfirmasi"
    )

    // === AUTO-CLEAN TIMING (ms) ===
    const val NAVIGATION_DELAY_MS = 600L
    const val PHASE_TIMEOUT_MS = 5_000L

    // Sprint 5: 4 detik pacing anti-bot (ai_task.md §3.2)
    const val PACING_DELAY_MS = 4_000L

    // Sprint 5: Hard timeout per app
    const val HARD_TIMEOUT_MS = 15_000L
}
