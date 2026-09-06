package com.example.platinumcleaner.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.data.AppCleanerRepository
import com.example.platinumcleaner.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel untuk DashboardScreen.
 *
 * Sesuai MVVM: ViewModel memegang dan mengelola state UI.
 * Sesuai 04_performance_budget.md: Tidak ada operasi I/O di Main Thread.
 * Lifecycle-aware via AndroidViewModel + viewModelScope.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCleanerRepository(application)

    // === Exposed State Flows ===

    // State utama: daftar aplikasi dan status fetch data
    private val _appsState = MutableStateFlow<UiState<List<AppInfo>>>(UiState.Loading)
    val appsState: StateFlow<UiState<List<AppInfo>>> = _appsState.asStateFlow()

    // State metrik hero card (jumlah cache total, progress gauge)
    private val _metricState = MutableStateFlow(StorageMetricState())
    val metricState: StateFlow<StorageMetricState> = _metricState.asStateFlow()

    // State permission banner
    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    init {
        loadData()
    }

    /**
     * Titik masuk utama untuk memuat data. Mengecek izin sebelum fetch data.
     * Dipanggil di init dan saat user kembali dari Settings setelah grant izin.
     */
    fun loadData() {
        val context = getApplication<Application>()

        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            Log.d(Constants.TAG_VIEWMODEL, "Usage Access belum diberikan — menampilkan permission state")
            _needsPermission.value = true
            _appsState.value = UiState.PermissionRequired
            return
        }

        _needsPermission.value = false
        fetchAppsWithCache()
    }

    /**
     * Mengambil data nyata dari repository di background thread via viewModelScope.
     */
    private fun fetchAppsWithCache() {
        viewModelScope.launch {
            repository.getInstalledAppsWithCache().collect { state ->
                _appsState.value = state

                // Kalkulasi metrik hero card dari data nyata
                if (state is UiState.Success) {
                    val apps = state.data
                    _metricState.value = StorageMetricState(
                        reclaimableAmount = repository.calculateTotalCacheFormatted(apps),
                        sweepProgress = repository.calculateGaugeProgress(apps),
                        isPurging = false,
                        isCleaned = false
                    )
                    Log.d(Constants.TAG_VIEWMODEL, "Berhasil load ${apps.size} apps")
                }

                if (state is UiState.PermissionRequired) {
                    _needsPermission.value = true
                }
            }
        }
    }

    /**
     * Dipanggil saat user menekan tombol Clean Now.
     * Sprint 2: Update state sementara — logika klik otomatis AccessibilityService ada di Sprint 3.
     */
    fun triggerClean() {
        viewModelScope.launch {
            _metricState.value = _metricState.value.copy(isPurging = true)
            Log.d(Constants.TAG_VIEWMODEL, "Clean trigger — AccessibilityService akan diaktifkan di Sprint 3")

            // Simulasi delay 1.4s untuk Sprint 2
            kotlinx.coroutines.delay(1400)

            _metricState.value = _metricState.value.copy(
                isPurging = false,
                isCleaned = true,
                reclaimableAmount = "0.0",
                sweepProgress = 0f
            )
        }
    }

    /**
     * Reset state setelah clean, untuk memuat ulang data segar.
     */
    fun resetAndReload() {
        _metricState.value = StorageMetricState()
        loadData()
    }
}
