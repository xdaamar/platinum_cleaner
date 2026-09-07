package com.example.platinumcleaner.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.example.platinumcleaner.R

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
    val reclaimableAmount: String = "1.2",
    val sweepProgress: Float = 0.68f,
    val isPurging: Boolean = false,
    val isCleaned: Boolean = false,
    val isCleaning: Boolean = false,
    val cleaningTarget: String? = null,
    val cleaningError: String? = null,
    val snackbarMessage: String? = null,
    // Sprint 5: Progress overlay fields
    val currentCleanIndex: Int = 0,
    val totalCleanApps: Int = 0,
    val currentCleanAppName: String? = null
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

// Real data models for Sprint 2
@Immutable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val cacheBytes: Long,
    val isSystemApp: Boolean = false
) {
    val cacheSizeFormatted: String
        get() = when {
            cacheBytes >= 1_073_741_824L -> String.format("%.1f GB", cacheBytes / 1_073_741_824.0)
            cacheBytes >= 1_048_576L -> String.format("%.0f MB", cacheBytes / 1_048_576.0)
            cacheBytes >= 1_024L -> String.format("%.0f KB", cacheBytes / 1_024.0)
            else -> "$cacheBytes B"
        }
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object PermissionRequired : UiState<Nothing>()
}
