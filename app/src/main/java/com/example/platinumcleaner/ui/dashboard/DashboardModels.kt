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
    val isCleaned: Boolean = false
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
