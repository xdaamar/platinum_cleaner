package com.example.platinumcleaner

/**
 * Central constants to avoid magic strings/numbers across the codebase.
 * Sesuai 02_coding_standards.md: No Magic Numbers/Strings.
 */
object Constants {
    // === SECURITY: AccessibilityService Scope Limitation ===
    // HUKUM BESI: Service HANYA boleh berinteraksi dengan package ini (03_security_protocols.md)
    const val SETTINGS_PACKAGE = "com.android.settings"

    // === LOGGING TAGS ===
    const val TAG_SERVICE = "PlatinumCleanerService"
    const val TAG_REPO = "AppCleanerRepository"
    const val TAG_VIEWMODEL = "DashboardViewModel"

    // === CACHE FORMATTING ===
    const val BYTES_PER_KB = 1_024L
    const val BYTES_PER_MB = 1_048_576L
    const val BYTES_PER_GB = 1_073_741_824L

    // === UI ===
    const val MAX_DASHBOARD_APP_ITEMS = 5
}
