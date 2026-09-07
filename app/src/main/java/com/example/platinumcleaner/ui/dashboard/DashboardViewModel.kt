package com.example.platinumcleaner.ui.dashboard

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.data.AppCleanerRepository
import com.example.platinumcleaner.service.CleanerEvent
import com.example.platinumcleaner.service.CleanSessionManager
import com.example.platinumcleaner.service.ServiceEventBus
import com.example.platinumcleaner.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — Sprint 5 Update.
 *
 * Perubahan:
 * - Handle CleanerEvent.ProgressUpdate → update overlay fields real-time
 * - Handle CleanerEvent.AllCompleted → reset state dan reload data
 * - triggerCleanLargest() tetap clean satu app (entry point utama)
 * - triggerCleanAll() baru untuk batch mode
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCleanerRepository(application)

    private val _appsState = MutableStateFlow<UiState<List<AppInfo>>>(UiState.Loading)
    val appsState: StateFlow<UiState<List<AppInfo>>> = _appsState.asStateFlow()

    private val _metricState = MutableStateFlow(StorageMetricState())
    val metricState: StateFlow<StorageMetricState> = _metricState.asStateFlow()

    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    init {
        loadData()
        observeServiceEvents()
    }

    // ===================================================
    // Data Loading
    // ===================================================

    fun loadData() {
        val context = getApplication<Application>()
        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            _needsPermission.value = true
            _appsState.value = UiState.PermissionRequired
            return
        }
        _needsPermission.value = false
        fetchAppsWithCache()
    }

    fun refreshData() {
        if (CleanSessionManager.isActive) return
        loadData()
    }

    private fun fetchAppsWithCache() {
        viewModelScope.launch {
            repository.getInstalledAppsWithCache().collect { state ->
                _appsState.value = state
                if (state is UiState.Success) {
                    val apps = state.data
                    _metricState.value = _metricState.value.copy(
                        reclaimableAmount = repository.calculateTotalCacheFormatted(apps),
                        sweepProgress = repository.calculateGaugeProgress(apps),
                        isPurging = false,
                        isCleaned = false,
                        isCleaning = false,
                        cleaningTarget = null,
                        currentCleanIndex = 0,
                        totalCleanApps = 0,
                        currentCleanAppName = null
                    )
                }
                if (state is UiState.PermissionRequired) _needsPermission.value = true
            }
        }
    }

    // ===================================================
    // ServiceEventBus Observer (viewModelScope → no memory leak)
    // ===================================================

    private fun observeServiceEvents() {
        viewModelScope.launch {
            ServiceEventBus.events.collect { event ->
                Log.d(Constants.TAG_VIEWMODEL, "Event dari Service V2: $event")
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: CleanerEvent) {
        when (event) {
            is CleanerEvent.Started -> {
                _metricState.value = _metricState.value.copy(
                    isCleaning = true,
                    cleaningTarget = event.packageName,
                    cleaningError = null,
                    snackbarMessage = null,
                    currentCleanIndex = CleanSessionManager.currentIndex + 1,
                    totalCleanApps = CleanSessionManager.totalApps,
                    currentCleanAppName = event.packageName
                )
            }

            // Sprint 5: Update overlay real-time
            is CleanerEvent.ProgressUpdate -> {
                _metricState.value = _metricState.value.copy(
                    isCleaning = true,
                    currentCleanIndex = event.currentIndex,
                    totalCleanApps = event.totalApps,
                    currentCleanAppName = event.currentAppName,
                    cleaningTarget = event.currentAppName
                )
            }

            is CleanerEvent.Success -> {
                // Untuk batch mode, jangan langsung reset — tunggu AllCompleted
                if (CleanSessionManager.totalApps <= 1) {
                    _metricState.value = _metricState.value.copy(
                        isCleaning = false,
                        cleaningTarget = null,
                        isPurging = false,
                        isCleaned = true,
                        reclaimableAmount = "0.0",
                        sweepProgress = 0f,
                        snackbarMessage = "Cache berhasil dibersihkan ✓"
                    )
                    viewModelScope.launch {
                        delay(1_000)
                        loadData()
                    }
                }
            }

            is CleanerEvent.Failed -> {
                val humanMessage = when {
                    event.reason.contains("cancelled", ignoreCase = true) ->
                        "Pembersihan dibatalkan."
                    event.reason.contains("not recognized", ignoreCase = true) ||
                            event.reason.contains("not found", ignoreCase = true) ->
                        "Tidak dapat menemukan tombol hapus cache. Silakan hapus manual."
                    event.reason.contains("Interrupted", ignoreCase = true) ->
                        "Proses terganggu oleh sistem. Silakan coba lagi."
                    event.reason.contains("timeout", ignoreCase = true) ->
                        "Navigasi terlalu lambat. Pastikan Accessibility Service aktif."
                    else -> "Pembersihan gagal. Silakan hapus cache secara manual."
                }
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    cleaningTarget = null,
                    isPurging = false,
                    cleaningError = event.reason,
                    snackbarMessage = humanMessage
                )
            }

            // Sprint 5: Semua app dalam antrian selesai
            is CleanerEvent.AllCompleted -> {
                Log.d(Constants.TAG_VIEWMODEL, "🏁 Semua app selesai dibersihkan")
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    isPurging = false,
                    isCleaned = true,
                    reclaimableAmount = "0.0",
                    sweepProgress = 0f,
                    cleaningTarget = null,
                    currentCleanIndex = 0,
                    totalCleanApps = 0,
                    currentCleanAppName = null,
                    snackbarMessage = "Semua cache berhasil dibersihkan ✓"
                )
                viewModelScope.launch {
                    delay(1_000)
                    loadData()
                }
            }
        }
    }

    // ===================================================
    // Clean Triggers
    // ===================================================

    fun initiateCleanForApp(packageName: String) {
        val context = getApplication<Application>()
        val sessionStarted = CleanSessionManager.startSession(packageName)
        if (!sessionStarted) {
            Log.w(Constants.TAG_VIEWMODEL, "Sesi masih aktif — diabaikan")
            return
        }

        _metricState.value = _metricState.value.copy(
            isPurging = true,
            isCleaning = true,
            cleaningTarget = packageName,
            cleaningError = null,
            snackbarMessage = null,
            currentCleanIndex = 1,
            totalCleanApps = 1,
            currentCleanAppName = packageName
        )

        viewModelScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Started(packageName))
        }

        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun triggerCleanLargest() {
        val state = _appsState.value
        if (state is UiState.Success && state.data.isNotEmpty()) {
            initiateCleanForApp(state.data.first().packageName)
        }
    }

    fun onSnackbarShown() {
        _metricState.value = _metricState.value.copy(snackbarMessage = null)
    }

    fun resetAndReload() {
        _metricState.value = StorageMetricState()
        loadData()
    }
}
