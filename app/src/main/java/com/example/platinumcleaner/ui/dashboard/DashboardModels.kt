package com.example.platinumcleaner.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.example.platinumcleaner.R
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningResult

@Immutable
data class AppCacheItem(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val packageRes: Int,
    @StringRes val sizeRes: Int,
    @DrawableRes val iconRes: Int,
    val isPrimary: Boolean = false
)

@Immutable
data class StorageMetricState(
    // Sprint 1–5 fields
    val reclaimableAmount: String = "0.0",
    val sweepProgress: Float = 0.68f,
    val isPurging: Boolean = false,
    val isCleaned: Boolean = false,
    val isCleaning: Boolean = false,
    val cleaningTarget: String? = null,
    val cleaningError: String? = null,
    val snackbarMessage: String? = null,
    val currentCleanIndex: Int = 0,
    val totalCleanApps: Int = 0,
    val currentCleanAppName: String? = null,
    // Sprint 6: Capability-based fields
    /** Strategy yang sedang/terakhir digunakan — ditampilkan di UI (honest wording §22) */
    val activeStrategy: CleaningCapability? = null,
    /** Hasil terverifikasi dari sesi terakhir */
    val lastCleaningResult: CleaningResult? = null
)

enum class DashboardNavTab(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int
) {
    DASHBOARD(R.string.nav_dashboard, R.drawable.ic_nav_dashboard),
    ANALYTICS(R.string.nav_analytics, R.drawable.ic_nav_analytics),
    RULES(R.string.nav_rules, R.drawable.ic_nav_rules),
    SETTINGS(R.string.nav_settings, R.drawable.ic_nav_settings)
}

enum class ScanMeasurementStatus {
    MEASURED,
    UNAVAILABLE,
    ERROR
}

// Real data models for Sprint 2 & V8 inventory realignment
@Immutable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val cacheBytes: Long,
    val isSystemApp: Boolean = false,
    val isLaunchable: Boolean = true,
    val isEnabled: Boolean = true,
    val uid: Int = 0,
    val scanStatus: ScanMeasurementStatus = ScanMeasurementStatus.MEASURED
) {
    val cacheSizeFormatted: String
        get() = when {
            cacheBytes >= 1_073_741_824L -> String.format("%.1f GB", cacheBytes / 1_073_741_824.0)
            cacheBytes >= 1_048_576L -> String.format("%.0f MB", cacheBytes / 1_048_576.0)
            cacheBytes >= 1_024L -> String.format("%.0f KB", cacheBytes / 1_024.0)
            else -> "$cacheBytes B"
        }
}

enum class PackageCategoryFilter {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY
}

@Immutable
data class InventorySummary(
    val rawPackagesCount: Int = 0,
    val userAppsCount: Int = 0,
    val systemAppsCount: Int = 0,
    val launchableAppsCount: Int = 0,
    val measurableCacheAppsCount: Int = 0,
    val unmeasurablePackagesCount: Int = 0,
    val errorPackagesCount: Int = 0,
    val totalMeasuredCacheBytes: Long = 0L
)

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val apps: List<AppInfo>, val totalBytes: Long) : ScanState()
    data class Partial(val apps: List<AppInfo>, val unmeasurableCount: Int, val totalBytes: Long) : ScanState()
    object Empty : ScanState()
    data class Failed(val error: String) : ScanState()
    object PermissionRequired : ScanState()
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object PermissionRequired : UiState<Nothing>()
}

/**
 * CapabilityState — Status kemampuan pembersihan perangkat.
 *
 * Sprint 7: Untuk preflight UI sesuai ai_task.md §14.
 * Ditampilkan sebagai capability readiness card di dashboard.
 */
@Immutable
data class CapabilityState(
    /** Usage Access permission tersedia — diperlukan untuk scanner. */
    val hasUsageAccess: Boolean = false,
    /** ACTION_CLEAR_APP_CACHE tersedia di perangkat ini. */
    val hasSystemCacheSupport: Boolean = false,
    /** Accessibility service aktif (opsional, P3). */
    val hasAccessibilityEnabled: Boolean = false,
    /** True jika semua kapabilitas minimum tersedia untuk mulai scan. */
    val isReady: Boolean = false
)
