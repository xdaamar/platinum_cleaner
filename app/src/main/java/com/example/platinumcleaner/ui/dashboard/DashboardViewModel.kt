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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel untuk DashboardScreen.
 *
 * Sprint 3 Enhancement:
 * - Collect ServiceEventBus untuk menerima event dari AccessibilityService
 * - initiateCleanForApp() untuk memulai sesi auto-clean
 *
 * Sesuai MVVM: ViewModel memegang dan mengelola state UI.
 * Sesuai 04_performance_budget.md: Tidak ada operasi I/O di Main Thread.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCleanerRepository(application)

    // === Exposed State Flows ===
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
            Log.d(Constants.TAG_VIEWMODEL, "Usage Access belum diberikan")
            _needsPermission.value = true
            _appsState.value = UiState.PermissionRequired
            return
        }

        _needsPermission.value = false
        fetchAppsWithCache()
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

    // ===================================================
    // Sprint 3: ServiceEventBus Observer
    //
    // Menggunakan viewModelScope sehingga otomatis cancelled
    // saat ViewModel di-clear — TIDAK ada memory leak.
    // ===================================================

    private fun observeServiceEvents() {
        viewModelScope.launch {
            ServiceEventBus.events.collect { event ->
                Log.d(Constants.TAG_VIEWMODEL, "Event dari Service: $event")
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
                    cleaningError = null
                )
            }

            is CleanerEvent.Success -> {
                Log.d(Constants.TAG_VIEWMODEL, "✅ Berhasil membersihkan: ${event.packageName}")
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    cleaningTarget = null,
                    isPurging = false,
                    isCleaned = true,
                    reclaimableAmount = "0.0",
                    sweepProgress = 0f
                )
                // Reload data setelah cache dibersihkan untuk angka terbaru
                viewModelScope.launch {
                    kotlinx.coroutines.delay(800)
                    loadData()
                }
            }

            is CleanerEvent.Failed -> {
                Log.e(Constants.TAG_VIEWMODEL, "❌ Gagal membersihkan ${event.packageName}: ${event.reason}")
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    cleaningTarget = null,
                    isPurging = false,
                    cleaningError = event.reason
                )
            }
        }
    }

    // ===================================================
    // Sprint 3: Auto-Clean Trigger
    // ===================================================

    /**
     * Memulai sesi auto-clean untuk satu aplikasi.
     *
     * Alur:
     * 1. Set CleanSessionManager dengan target package
     * 2. Emit CleanerEvent.Started ke ServiceEventBus
     * 3. Update UI state ke Cleaning
     * 4. Launch Intent ke halaman detail app di Settings
     *    → AccessibilityService akan menangkap dan mengendalikan navigasi selanjutnya
     *
     * @param packageName Package name dari app yang akan dibersihkan cache-nya.
     */
    fun initiateCleanForApp(packageName: String) {
        val context = getApplication<Application>()

        // Tandai sesi aktif — service hanya akan bekerja setelah ini
        CleanSessionManager.startSession(packageName)

        _metricState.value = _metricState.value.copy(
            isPurging = true,
            isCleaning = true,
            cleaningTarget = packageName,
            cleaningError = null
        )

        // Emit Started event
        viewModelScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Started(packageName))
        }

        // Buka halaman detail app di Settings — ini memicu AccessibilityService
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        Log.d(Constants.TAG_VIEWMODEL, "Membuka Settings untuk: $packageName")
    }

    /**
     * Trigger clean untuk app dengan cache terbesar (aksi tombol utama "Clean Now").
     */
    fun triggerCleanLargest() {
        val state = _appsState.value
        if (state is UiState.Success && state.data.isNotEmpty()) {
            initiateCleanForApp(state.data.first().packageName)
        } else {
            // Fallback: simulasi purge jika tidak ada data nyata
            viewModelScope.launch {
                _metricState.value = _metricState.value.copy(isPurging = true)
                kotlinx.coroutines.delay(1400)
                _metricState.value = _metricState.value.copy(
                    isPurging = false,
                    isCleaned = true,
                    reclaimableAmount = "0.0",
                    sweepProgress = 0f
                )
            }
        }
    }

    fun resetAndReload() {
        _metricState.value = StorageMetricState()
        loadData()
    }
}
