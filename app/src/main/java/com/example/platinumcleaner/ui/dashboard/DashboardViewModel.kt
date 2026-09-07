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
 * ViewModel untuk DashboardScreen.
 *
 * Sprint 4 Hardening:
 * - Guard double-execution di initiateCleanForApp()
 * - Snackbar manusiawi untuk error & sukses
 * - refreshData() untuk dipanggil dari ON_RESUME lifecycle observer
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
            Log.d(Constants.TAG_VIEWMODEL, "Usage Access belum diberikan")
            _needsPermission.value = true
            _appsState.value = UiState.PermissionRequired
            return
        }
        _needsPermission.value = false
        fetchAppsWithCache()
    }

    /**
     * Dipanggil saat app kembali ke foreground (ON_RESUME dari DashboardScreen).
     * Hanya fetch ulang jika tidak sedang ada sesi cleaning aktif.
     */
    fun refreshData() {
        if (CleanSessionManager.isActive) {
            Log.d(Constants.TAG_VIEWMODEL, "refreshData() diabaikan — sesi cleaning masih aktif")
            return
        }
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
                        cleaningTarget = null
                    )
                }
                if (state is UiState.PermissionRequired) {
                    _needsPermission.value = true
                }
            }
        }
    }

    // ===================================================
    // ServiceEventBus Observer (no memory leak — viewModelScope)
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
                    cleaningError = null,
                    snackbarMessage = null
                )
            }

            is CleanerEvent.Success -> {
                Log.d(Constants.TAG_VIEWMODEL, "✅ Berhasil: ${event.packageName}")
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    cleaningTarget = null,
                    isPurging = false,
                    isCleaned = true,
                    reclaimableAmount = "0.0",
                    sweepProgress = 0f,
                    snackbarMessage = "Cache berhasil dibersihkan ✓"
                )
                // Reload data segar setelah 1 detik
                viewModelScope.launch {
                    delay(1_000)
                    loadData()
                }
            }

            is CleanerEvent.Failed -> {
                Log.e(Constants.TAG_VIEWMODEL, "❌ Gagal: ${event.reason}")
                // Terjemahkan reason teknis menjadi pesan manusiawi
                val humanMessage = when {
                    event.reason.contains("cancelled", ignoreCase = true) ->
                        "Pembersihan dibatalkan."
                    event.reason.contains("not recognized", ignoreCase = true) ->
                        "Tidak dapat menemukan tombol hapus cache. Silakan hapus manual di Pengaturan."
                    event.reason.contains("Interrupted", ignoreCase = true) ->
                        "Proses terganggu oleh sistem. Silakan coba lagi."
                    event.reason.contains("Timeout", ignoreCase = true) ->
                        "Navigasi terlalu lambat. Pastikan Accessibility Service aktif, lalu coba lagi."
                    else ->
                        "Pembersihan gagal. Silakan hapus cache secara manual."
                }
                _metricState.value = _metricState.value.copy(
                    isCleaning = false,
                    cleaningTarget = null,
                    isPurging = false,
                    cleaningError = event.reason,
                    snackbarMessage = humanMessage
                )
            }
        }
    }

    // ===================================================
    // Auto-Clean Trigger
    // ===================================================

    /**
     * Sprint 4: Guard double-execution.
     * Jika CleanSessionManager.isActive → skip, jangan buka Settings dua kali.
     */
    fun initiateCleanForApp(packageName: String) {
        val context = getApplication<Application>()

        // GUARD: Tolak jika sesi sebelumnya masih aktif
        val sessionStarted = CleanSessionManager.startSession(packageName)
        if (!sessionStarted) {
            Log.w(Constants.TAG_VIEWMODEL, "Sesi masih aktif — request diabaikan (anti-spam)")
            return
        }

        _metricState.value = _metricState.value.copy(
            isPurging = true,
            isCleaning = true,
            cleaningTarget = packageName,
            cleaningError = null,
            snackbarMessage = null
        )

        viewModelScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Started(packageName))
        }

        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)

        Log.d(Constants.TAG_VIEWMODEL, "Membuka Settings untuk: $packageName")
    }

    fun triggerCleanLargest() {
        val state = _appsState.value
        if (state is UiState.Success && state.data.isNotEmpty()) {
            initiateCleanForApp(state.data.first().packageName)
        } else {
            viewModelScope.launch {
                _metricState.value = _metricState.value.copy(isPurging = true)
                delay(1_400)
                _metricState.value = _metricState.value.copy(
                    isPurging = false,
                    isCleaned = true,
                    reclaimableAmount = "0.0",
                    sweepProgress = 0f
                )
            }
        }
    }

    /** Hapus snackbar message setelah ditampilkan (one-shot). */
    fun onSnackbarShown() {
        _metricState.value = _metricState.value.copy(snackbarMessage = null)
    }

    fun resetAndReload() {
        _metricState.value = StorageMetricState()
        loadData()
    }
}
