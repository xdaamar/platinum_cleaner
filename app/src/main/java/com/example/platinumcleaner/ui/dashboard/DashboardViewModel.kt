package com.example.platinumcleaner.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.data.AppCleanerRepository
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.domain.verification.VerificationEngine
import com.example.platinumcleaner.platform.cleaning.CapabilityResolver
import com.example.platinumcleaner.platform.cleaning.CleaningOrchestrator
import com.example.platinumcleaner.service.CleanSessionManager
import com.example.platinumcleaner.service.CleanerEvent
import com.example.platinumcleaner.service.ServiceEventBus
import com.example.platinumcleaner.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — Sprint 6 Refactor.
 *
 * Perubahan utama:
 * - Integrate CleaningOrchestrator sebagai brain cleaning
 * - triggerSmartClean() memanggil Orchestrator, bukan langsung ke Service
 * - onResume() memicu verifyAfterResume() untuk honest result
 * - Handle CleaningResult domain model (bukan ServiceEventBus untuk flow utama)
 * - ServiceEventBus tetap untuk backward compatibility Accessibility path
 *
 * Sesuai ai_task.md §22: Honest UI terminology.
 * Sesuai ai_task.md §38: No hardcoded success.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCleanerRepository(application)
    private val orchestrator = CleaningOrchestrator()

    private val _appsState = MutableStateFlow<UiState<List<AppInfo>>>(UiState.Loading)
    val appsState: StateFlow<UiState<List<AppInfo>>> = _appsState.asStateFlow()

    private val _metricState = MutableStateFlow(StorageMetricState())
    val metricState: StateFlow<StorageMetricState> = _metricState.asStateFlow()

    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    // Pending result yang menunggu verification setelah ON_RESUME
    private var pendingCleaningResult: CleaningResult? = null

    init {
        loadData()
        observeServiceEvents() // Backward compat untuk Accessibility path
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

    /**
     * Dipanggil dari DisposableEffect ON_RESUME di DashboardScreen.
     *
     * Dua skenario:
     * 1. Ada pendingCleaningResult → verify (user kembali dari Settings)
     * 2. Tidak ada → refresh data biasa
     */
    fun onAppResumed() {
        val pending = pendingCleaningResult
        Log.d(Constants.TAG_CLEAN, "[LIFECYCLE] onResume | hasPendingResult=${pending != null} | sessionState=${CleanSessionManager.currentState} | sessionActive=${CleanSessionManager.isActive}")
        if (pending != null && CleanSessionManager.currentState ==
            CleanSessionManager.SessionState.WAITING_FOR_RESUME) {
            Log.d(Constants.TAG_VIEWMODEL, "ON_RESUME: ada pending result — mulai verifikasi")
            Log.d(Constants.TAG_CLEAN, "[LIFECYCLE] onResume → triggering verification pipeline")
            verifyAfterResume(pending)
        } else if (!CleanSessionManager.isActive) {
            Log.d(Constants.TAG_VIEWMODEL, "ON_RESUME: refresh data biasa")
            Log.d(Constants.TAG_CLEAN, "[LIFECYCLE] onResume → no active session, refreshing data")
            loadData()
        }
    }

    // ===================================================
    // Sprint 6: Smart Clean via Orchestrator
    // ===================================================

    /**
     * Entry point utama user: tombol "Smart Clean".
     *
     * Flow:
     * 1. Ambil list apps dengan cache terbesar
     * 2. Snapshot beforeBytes
     * 3. Resolve capability via CapabilityResolver
     * 4. Eksekusi via Orchestrator → dapat CleaningResult
     * 5. Jika PENDING_VERIFICATION → simpan, tunggu ON_RESUME
     * 6. Jika langsung ada result → update UI
     */
    fun triggerSmartClean() {
        val state = _appsState.value
        if (state !is UiState.Success || state.data.isEmpty()) {
            Log.w(Constants.TAG_VIEWMODEL, "Tidak ada data app untuk dibersihkan")
            return
        }

        // Guard: jangan mulai jika sesi masih aktif
        if (CleanSessionManager.isActive) {
            Log.w(Constants.TAG_VIEWMODEL, "Sesi masih aktif — diabaikan")
            return
        }

        val context = getApplication<Application>()
        val targetApps = state.data.take(Constants.MAX_DASHBOARD_APP_ITEMS)
        val targetPackages = targetApps.map { it.packageName }

        // Snapshot beforeBytes dari data yang sudah ada (dari Scanner)
        val beforeBytes = targetApps.associate { it.packageName to it.cacheBytes }

        // Sprint 7: log context type — penting untuk diagnosa
        Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] triggerSmartClean | contextClass=${context.javaClass.simpleName} | targets=${targetPackages.size} | packages=$targetPackages")
        Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] beforeBytes snapshot: ${beforeBytes.entries.joinToString { "${it.key}=${it.value}B" }}")

        viewModelScope.launch {
            // Resolve capability dulu untuk update UI label
            val bestCapability = CapabilityResolver.resolveBest(context)

            Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] bestCapability=${bestCapability.name}")

            // Update UI: menunjukkan strategy yang digunakan (honest)
            _metricState.value = _metricState.value.copy(
                isCleaning = true,
                isPurging = true,
                cleaningError = null,
                snackbarMessage = null,
                activeStrategy = bestCapability,
                currentCleanIndex = 1,
                totalCleanApps = targetPackages.size
            )

            CleanSessionManager.startSession(targetPackages.first())
            CleanSessionManager.markExecuting(bestCapability)

            val request = CleaningRequest(
                targetPackages = targetPackages,
                preCleanCacheBytes = beforeBytes
            )

            Log.d(Constants.TAG_VIEWMODEL, "[CLEAN] Menggunakan strategy: ${bestCapability.name}")
            Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] calling orchestrator.execute()")

            val result = orchestrator.execute(context, request)

            Log.d(Constants.TAG_CLEAN, "[RESULT] orchestrator.execute() returned overallStatus=${result.overallStatus} capability=${result.selectedCapability.name}")

            when (result.overallStatus) {
                VerificationStatus.PENDING_VERIFICATION -> {
                    // Strategy membuka Settings — tunggu user kembali
                    pendingCleaningResult = result
                    CleanSessionManager.markWaitingForResume(result)
                    Log.d(Constants.TAG_CLEAN, "[LIFECYCLE] app meninggalkan foreground — menunggu onResume")
                    _metricState.value = _metricState.value.copy(
                        isCleaning = true,
                        currentCleanAppName = result.appResults.firstOrNull()?.packageName,
                        snackbarMessage = null
                    )
                }
                else -> {
                    // Langsung ada hasil (jarang terjadi di sprint ini)
                    Log.d(Constants.TAG_CLEAN, "[RESULT] immediate result (no system UI): ${result.overallStatus}")
                    handleFinalResult(result)
                }
            }
        }
    }

    /**
     * Clean satu app spesifik (dipanggil dari tombol "Clean" di list).
     */
    fun initiateCleanForApp(packageName: String) {
        val state = _appsState.value
        val appInfo = (state as? UiState.Success)?.data?.find { it.packageName == packageName }

        if (CleanSessionManager.isActive) {
            Log.w(Constants.TAG_VIEWMODEL, "Sesi masih aktif — diabaikan")
            return
        }

        val context = getApplication<Application>()
        val beforeBytes = appInfo?.cacheBytes ?: 0L

        viewModelScope.launch {
            val bestCapability = CapabilityResolver.resolveBest(context)

            _metricState.value = _metricState.value.copy(
                isCleaning = true,
                isPurging = true,
                cleaningTarget = packageName,
                activeStrategy = bestCapability,
                snackbarMessage = null
            )

            CleanSessionManager.startSession(packageName)
            CleanSessionManager.markExecuting(bestCapability)

            val request = CleaningRequest(
                targetPackages = listOf(packageName),
                preCleanCacheBytes = mapOf(packageName to beforeBytes)
            )

            val result = orchestrator.execute(context, request)

            when (result.overallStatus) {
                VerificationStatus.PENDING_VERIFICATION -> {
                    pendingCleaningResult = result
                    CleanSessionManager.markWaitingForResume(result)
                }
                else -> handleFinalResult(result)
            }
        }
    }

    // ===================================================
    // Verification after ON_RESUME
    // ===================================================

    private fun verifyAfterResume(pending: CleaningResult) {
        val context = getApplication<Application>()
        CleanSessionManager.markVerifying()
        Log.d(Constants.TAG_CLEAN, "[VERIFICATION] verifyAfterResume starting | pendingPackages=${pending.appResults.map { it.packageName }} | beforeBytes=${pending.appResults.map { "${it.packageName}:${it.beforeBytes}B" }}")

        viewModelScope.launch {
            val verified = orchestrator.verifyAfterResume(context, pending)
            Log.d(Constants.TAG_CLEAN, "[VERIFICATION] verifyAfterResume done | overallStatus=${verified.overallStatus} | totalReclaimed=${verified.totalReclaimedBytes}B")
            pendingCleaningResult = null
            handleFinalResult(verified)
        }
    }

    private fun handleFinalResult(result: CleaningResult) {
        CleanSessionManager.markCompleted()

        val totalReclaimed = result.totalReclaimedBytes
        val reclaimedText = VerificationEngine.formatReclaimedVerified(totalReclaimed)
        val isAnySuccess = result.successCount > 0

        // Sprint 7: Pesan jujur per state (ai_task.md §17)
        // Tidak ada generic "Pembersihan gagal" — setiap state punya penjelasan spesifik
        val snackbar = when (result.overallStatus) {
            VerificationStatus.VERIFIED_SUCCESS ->
                "✓ Pembersihan selesai — $reclaimedText"

            VerificationStatus.VERIFIED_PARTIAL ->
                "Pembersihan sebagian selesai — $reclaimedText. Beberapa data mungkin masih digunakan sistem."

            VerificationStatus.PARTIAL_SUCCESS ->
                "Sebagian berhasil — $reclaimedText" // backward compat

            VerificationStatus.NO_MEASURABLE_CHANGE ->
                "Permintaan pembersihan berhasil dikirim ke Android, tetapi tidak ada pengurangan cache yang dapat diukur. Ini tidak selalu berarti gagal."

            VerificationStatus.NO_CHANGE ->
                "Cache sudah bersih atau tidak berubah" // backward compat

            VerificationStatus.USER_CANCELLED ->
                "Pembersihan dibatalkan. Tidak ada perubahan yang dilakukan oleh Platinum Cleaner."

            VerificationStatus.INTENT_LAUNCH_FAILED ->
                "Android tidak dapat membuka fitur pembersihan cache pada perangkat ini."

            VerificationStatus.INTENT_UNAVAILABLE ->
                "Fitur pembersihan sistem tidak tersedia di perangkat ini. Gunakan pembersihan manual."

            VerificationStatus.VERIFICATION_TIMEOUT ->
                "Android belum memberikan hasil yang dapat diverifikasi. Silakan coba lagi atau gunakan pembersihan manual."

            VerificationStatus.WAITING_FOR_SYSTEM_ACTION ->
                "Android sedang memproses pembersihan cache. Tunggu sebentar..."

            VerificationStatus.UNSUPPORTED ->
                "Perangkat ini tidak mendukung pembersihan cache otomatis."

            VerificationStatus.FAILED ->
                "Pembersihan gagal dieksekusi. Silakan hapus cache secara manual melalui Pengaturan."

            VerificationStatus.UNKNOWN ->
                "Hasil tidak dapat diverifikasi."

            VerificationStatus.VERIFYING, VerificationStatus.PENDING_VERIFICATION -> null
        }

        Log.d(
            Constants.TAG_VIEWMODEL,
            "[RESULT] overall=${result.overallStatus} " +
                    "reclaimed=${VerificationEngine.formatBytes(totalReclaimed)} " +
                    "success=${result.successCount} fail=${result.failedCount}"
        )
        Log.d(Constants.TAG_CLEAN, "[RESULT] final | overallStatus=${result.overallStatus} | reclaimed=${VerificationEngine.formatBytes(totalReclaimed)} | snackbar=${snackbar?.take(60)}")

        viewModelScope.launch {
            _metricState.value = _metricState.value.copy(
                isCleaning = false,
                isPurging = false,
                isCleaned = isAnySuccess,
                lastCleaningResult = result,
                snackbarMessage = snackbar,
                currentCleanIndex = 0,
                totalCleanApps = 0,
                currentCleanAppName = null,
                activeStrategy = null,
                // Update angka dengan honest wording
                reclaimableAmount = if (isAnySuccess)
                    VerificationEngine.formatBytes(totalReclaimed)
                else _metricState.value.reclaimableAmount
            )

            // Reload data segar setelah 1 detik
            delay(1_000)
            loadData()
        }
    }


    // ===================================================
    // Backward Compat: ServiceEventBus (untuk Accessibility path)
    // ===================================================

    private fun observeServiceEvents() {
        viewModelScope.launch {
            ServiceEventBus.events.collect { event ->
                // Hanya handle events jika ada sesi Accessibility aktif
                // dan tidak ada pending result dari Orchestrator
                if (pendingCleaningResult != null) return@collect

                when (event) {
                    is CleanerEvent.ProgressUpdate -> {
                        _metricState.value = _metricState.value.copy(
                            currentCleanIndex = event.currentIndex,
                            totalCleanApps = event.totalApps,
                            currentCleanAppName = event.currentAppName
                        )
                    }
                    is CleanerEvent.AllCompleted -> {
                        // Akan di-handle oleh onAppResumed → verifyAfterResume
                    }
                    else -> { /* Other events handled by Orchestrator */ }
                }
            }
        }
    }

    // ===================================================
    // Utility
    // ===================================================

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
                    val totalCache = repository.calculateTotalCacheFormatted(apps)
                    _metricState.value = _metricState.value.copy(
                        reclaimableAmount = totalCache,
                        sweepProgress = repository.calculateGaugeProgress(apps),
                        isPurging = false,
                        isCleaning = false,
                        currentCleanIndex = 0,
                        totalCleanApps = 0,
                        currentCleanAppName = null,
                        activeStrategy = null
                    )
                }
                if (state is UiState.PermissionRequired) _needsPermission.value = true
            }
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
