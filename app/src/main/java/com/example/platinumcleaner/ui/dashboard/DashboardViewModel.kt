package com.example.platinumcleaner.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.data.AppCleanerRepository
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningMode
import com.example.platinumcleaner.domain.cleaning.CleaningPlan
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.domain.verification.VerificationEngine
import com.example.platinumcleaner.platform.cleaning.CapabilityResolver
import com.example.platinumcleaner.platform.cleaning.CleaningOrchestrator
import com.example.platinumcleaner.platform.cleaning.SystemCacheStrategy
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

    // Sprint 7: Capability preflight state
    private val _capabilityState = MutableStateFlow(CapabilityState())
    val capabilityState: StateFlow<CapabilityState> = _capabilityState.asStateFlow()

    // V8: Authoritative inventory summary
    private val _inventorySummary = MutableStateFlow(InventorySummary())
    val inventorySummary: StateFlow<InventorySummary> = _inventorySummary.asStateFlow()

    // V9: Authoritative ScanResult (§39)
    private val _scanResult = MutableStateFlow(com.example.platinumcleaner.domain.inventory.ScanResult())
    val scanResult: StateFlow<com.example.platinumcleaner.domain.inventory.ScanResult> = _scanResult.asStateFlow()

    // V8: Explicit Scan State
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Pending result yang menunggu verification setelah ON_RESUME
    private var pendingCleaningResult: CleaningResult? = null

    init {
        loadData()
        checkCapabilities()
        observeServiceEvents() // Backward compat untuk Accessibility path
    }

    // ===================================================
    // Data Loading
    // ===================================================

    fun loadData() {
        val context = getApplication<Application>()
        val hasUsageAccess = PermissionHelper.hasUsageStatsPermission(context)
        Log.d(Constants.TAG_CLEAN, "[PERMISSION] loadData | PACKAGE_USAGE_STATS granted=$hasUsageAccess")
        if (!hasUsageAccess) {
            _needsPermission.value = true
            _appsState.value = UiState.PermissionRequired
            checkCapabilities() // Update preflight state
            return
        }
        _needsPermission.value = false
        fetchAppsWithCache()
        checkCapabilities()
    }

    /**
     * Sprint 7: Check semua capability dan update PreflightState.
     * Dipanggil saat init, loadData(), dan onAppResumed().
     */
    fun checkCapabilities() {
        val context = getApplication<Application>()
        val hasUsageAccess = PermissionHelper.hasUsageStatsPermission(context)
        val hasSystemCacheSupport = CapabilityResolver.isSystemWideCacheAvailable(context)
        val hasAccessibility = CapabilityResolver.isAccessibilityAutomationAvailable(context)

        Log.d(Constants.TAG_CLEAN, "[PERMISSION] preflight | usageAccess=$hasUsageAccess | systemCache=$hasSystemCacheSupport | accessibility=$hasAccessibility")

        _capabilityState.value = CapabilityState(
            hasUsageAccess = hasUsageAccess,
            hasSystemCacheSupport = hasSystemCacheSupport,
            hasAccessibilityEnabled = hasAccessibility,
            isReady = hasUsageAccess // Ready jika minimal usage access tersedia
        )
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

        // Sprint 7: Selalu re-check capabilities saat resume — user mungkin baru grant permission
        checkCapabilities()

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
    /**
     * Entry point utama user: tombol "Clean Now" / "Smart Clean".
     * V8 Architecture (§2, §16, §17, §18):
     * - Jika mode == SYSTEM_WIDE: Eksekusi satu operasi sistem tanpa antrian palsu 5 app.
     * - Jika mode == PER_APP: Eksekusi antrian per-app batch tanpa limit buatan 5 aplikasi.
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
        // Self-protection (§47) & Zero-cache policy (§6, §27):
        // Target pembersihan HANYA aplikasi yang memiliki cache > 0 B dan bukan package cleaner sendiri
        val eligibleApps = state.data
            .filter { it.packageName != context.packageName }
            .filter { it.cacheBytes > 0L }

        if (eligibleApps.isEmpty()) {
            Log.w(Constants.TAG_VIEWMODEL, "Tidak ada target pembersihan yang eligible (cache 0 B)")
            _metricState.value = _metricState.value.copy(
                snackbarMessage = "Semua aplikasi sudah bersih dari cache (0 B)."
            )
            return
        }

        val mode = CapabilityResolver.resolveMode(context, isTargetedClean = false)

        viewModelScope.launch {
            if (mode == CleaningMode.SYSTEM_WIDE) {
                // V8 FIX: Mode SYSTEM_WIDE adalah 1 operasi sistem
                val totalBeforeBytes = eligibleApps.sumOf { it.cacheBytes }
                val beforeBytesMap = eligibleApps.associate { it.packageName to it.cacheBytes }

                Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] triggerSmartClean (SYSTEM_WIDE) | eligibleApps=${eligibleApps.size} | totalEstimated=${VerificationEngine.formatBytes(totalBeforeBytes)}")

                _metricState.value = _metricState.value.copy(
                    isCleaning = true,
                    isPurging = true,
                    cleaningError = null,
                    snackbarMessage = null,
                    activeStrategy = CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST,
                    currentCleanIndex = 1,
                    totalCleanApps = 1,
                    currentCleanAppName = "Penyimpanan Sistem Android"
                )

                CleanSessionManager.startSession(SystemCacheStrategy.SYSTEM_TARGET_PACKAGE)
                CleanSessionManager.markExecuting(CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST)

                val plan = CleaningPlan(
                    mode = CleaningMode.SYSTEM_WIDE,
                    targets = emptyList(),
                    totalTargets = 1,
                    estimatedReclaim = totalBeforeBytes
                )

                val result = orchestrator.executePlan(context, plan, beforeBytesMap)

                when (result.overallStatus) {
                    VerificationStatus.WAITING_FOR_SYSTEM_ACTION,
                    VerificationStatus.PENDING_VERIFICATION -> {
                        pendingCleaningResult = result
                        CleanSessionManager.markWaitingForResume(result)
                        _metricState.value = _metricState.value.copy(
                            isCleaning = true,
                            currentCleanAppName = "Penyimpanan Sistem Android"
                        )
                    }
                    else -> handleFinalResult(result)
                }
            } else {
                // Mode Per-App (Assisted atau Automated) — V8 FIX (§16, §17): Hapus limit 5 aplikasi!
                val targetPackages = eligibleApps.map { it.packageName }
                val beforeBytesMap = eligibleApps.associate { it.packageName to it.cacheBytes }
                val capability = if (mode == CleaningMode.PER_APP_AUTOMATED) {
                    CleaningCapability.ACCESSIBILITY_AUTOMATION
                } else {
                    CleaningCapability.PER_APP_NAVIGATION
                }

                Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] triggerSmartClean (PER_APP) | targets=${targetPackages.size} | capability=$capability")

                _metricState.value = _metricState.value.copy(
                    isCleaning = true,
                    isPurging = true,
                    cleaningError = null,
                    snackbarMessage = null,
                    activeStrategy = capability,
                    currentCleanIndex = 1,
                    totalCleanApps = targetPackages.size,
                    currentCleanAppName = eligibleApps.first().appName
                )

                val targets = eligibleApps.map { app ->
                    com.example.platinumcleaner.domain.cleaning.CleaningTarget(
                        packageName = app.packageName,
                        appLabel = app.appName,
                        cacheBytesBefore = app.cacheBytes,
                        isEligible = true
                    )
                }
                CleanSessionManager.startBatchSessionWithTargets(targets, mode)
                CleanSessionManager.markExecuting(capability)

                val plan = CleaningPlan(
                    mode = mode,
                    targets = targetPackages,
                    totalTargets = targetPackages.size,
                    estimatedReclaim = eligibleApps.sumOf { it.cacheBytes }
                )

                val result = orchestrator.executePlan(context, plan, beforeBytesMap)

                when (result.overallStatus) {
                    VerificationStatus.PENDING_VERIFICATION -> {
                        pendingCleaningResult = result
                        CleanSessionManager.markWaitingForResume(result)
                        _metricState.value = _metricState.value.copy(
                            isCleaning = true,
                            currentCleanAppName = eligibleApps.first().appName
                        )
                    }
                    else -> handleFinalResult(result)
                }
            }
        }
    }

    /**
     * V8 FIX (§23, §24, §54): Clean satu app spesifik (dipanggil dari tombol "Bersihkan" di list).
     * Selalu menggunakan per-app mode (PER_APP_AUTOMATED atau PER_APP_ASSISTED),
     * TIDAK PERNAH menggunakan system-wide!
     */
    fun initiateCleanForApp(packageName: String) {
        val state = _appsState.value
        val appInfo = (state as? UiState.Success)?.data?.find { it.packageName == packageName }

        if (CleanSessionManager.isActive) {
            Log.w(Constants.TAG_VIEWMODEL, "Sesi masih aktif — diabaikan")
            return
        }

        val context = getApplication<Application>()
        // Self-protection (§45): Dilarang membersihkan diri sendiri
        if (packageName == context.packageName) {
            Log.w(Constants.TAG_VIEWMODEL, "Self-protection: tidak dapat membersihkan package sendiri")
            return
        }

        val beforeBytes = appInfo?.cacheBytes ?: 0L

        viewModelScope.launch {
            val mode = CapabilityResolver.resolveMode(context, isTargetedClean = true)
            val capability = if (mode == CleaningMode.PER_APP_AUTOMATED) {
                CleaningCapability.ACCESSIBILITY_AUTOMATION
            } else {
                CleaningCapability.PER_APP_NAVIGATION
            }

            Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] initiateCleanForApp | target=$packageName | mode=$mode | capability=$capability")

            _metricState.value = _metricState.value.copy(
                isCleaning = true,
                isPurging = true,
                cleaningTarget = packageName,
                currentCleanAppName = appInfo?.appName ?: packageName,
                currentCleanIndex = 1,
                totalCleanApps = 1,
                activeStrategy = capability,
                snackbarMessage = null
            )

            CleanSessionManager.startSession(packageName)
            CleanSessionManager.markExecuting(capability)

            val plan = CleaningPlan(
                mode = mode,
                targets = listOf(packageName),
                totalTargets = 1,
                estimatedReclaim = beforeBytes
            )

            val result = orchestrator.executePlan(
                context = context,
                plan = plan,
                preCleanCacheBytes = mapOf(packageName to beforeBytes)
            )

            when (result.overallStatus) {
                VerificationStatus.PENDING_VERIFICATION -> {
                    pendingCleaningResult = result
                    CleanSessionManager.markWaitingForResume(result)
                    _metricState.value = _metricState.value.copy(
                        isCleaning = true,
                        currentCleanAppName = appInfo?.appName ?: packageName
                    )
                }
                else -> handleFinalResult(result)
            }
        }
    }

    /**
     * V9 SPRINT FIX (§8, §30): Hentikan seluruh sesi pembersihan secara aman.
     */
    fun stopCleaning() {
        Log.d(Constants.TAG_CLEAN, "[STOP] User stopped cleaning session")
        CleanSessionManager.requestStop()
        cancelCleaning()
    }

    /**
     * V9 SPRINT FIX (§26): Lewati (skip) aplikasi saat ini dan lanjut ke aplikasi berikutnya.
     */
    fun skipCurrentApp() {
        Log.d(Constants.TAG_CLEAN, "[SKIP] User skipped current app")
        val nextPackage = CleanSessionManager.skipCurrent()
        if (nextPackage != null) {
            val appInfo = (_appsState.value as? UiState.Success)?.data?.find { it.packageName == nextPackage }
            _metricState.value = _metricState.value.copy(
                currentCleanIndex = CleanSessionManager.currentIndex + 1,
                currentCleanAppName = appInfo?.appName ?: nextPackage,
                snackbarMessage = "Aplikasi dilewati — beralih ke target berikutnya"
            )
        } else {
            cancelCleaning()
        }
    }

    /**
     * V8 & V9 FIX (§37, §74): Pembersihan Sistem Android (Secondary Feature).
     * Hanya dipanggil jika user secara eksplisit memilih fitur sistem, BUKAN dari Start Clean.
     */
    fun triggerSystemWideClean() {
        val state = _appsState.value
        if (state !is UiState.Success || state.data.isEmpty()) return
        if (CleanSessionManager.isActive) return

        val context = getApplication<Application>()
        val eligibleApps = state.data.filter { it.packageName != context.packageName && it.cacheBytes > 0L }
        val totalBeforeBytes = eligibleApps.sumOf { it.cacheBytes }
        val beforeBytesMap = eligibleApps.associate { it.packageName to it.cacheBytes }

        viewModelScope.launch {
            _metricState.value = _metricState.value.copy(
                isCleaning = true,
                isPurging = true,
                cleaningError = null,
                snackbarMessage = null,
                activeStrategy = CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST,
                currentCleanIndex = 1,
                totalCleanApps = 1,
                currentCleanAppName = "Penyimpanan Sistem Android"
            )

            CleanSessionManager.startSession(SystemCacheStrategy.SYSTEM_TARGET_PACKAGE)
            CleanSessionManager.markExecuting(CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST)

            val plan = CleaningPlan(
                mode = CleaningMode.SYSTEM_WIDE,
                targets = emptyList(),
                totalTargets = 1,
                estimatedReclaim = totalBeforeBytes
            )

            val result = orchestrator.executePlan(context, plan, beforeBytesMap)
            when (result.overallStatus) {
                VerificationStatus.WAITING_FOR_SYSTEM_ACTION,
                VerificationStatus.PENDING_VERIFICATION -> {
                    pendingCleaningResult = result
                    CleanSessionManager.markWaitingForResume(result)
                }
                else -> handleFinalResult(result)
            }
        }
    }

    /**
     * V8 FIX (§59): Batalkan sesi pembersihan aktif.
     */
    fun cancelCleaning() {
        Log.d(Constants.TAG_CLEAN, "[ORCHESTRATOR] User cancelled cleaning session")
        CleanSessionManager.cancelSession()
        pendingCleaningResult = null
        _metricState.value = _metricState.value.copy(
            isCleaning = false,
            isPurging = false,
            cleaningTarget = null,
            currentCleanAppName = null,
            snackbarMessage = "Pembersihan dihentikan"
        )
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

            VerificationStatus.USER_SKIPPED ->
                "Aplikasi dilewati oleh pengguna."

            VerificationStatus.STOPPED ->
                "Sesi pembersihan dihentikan oleh pengguna."

            VerificationStatus.AUTOMATION_FAILED ->
                "Otomasi aksesibilitas tidak dapat menemukan tombol hapus cache yang aman."

            VerificationStatus.NAVIGATION_FAILED ->
                "Gagal membuka halaman pengaturan aplikasi."

            VerificationStatus.TARGET_UNAVAILABLE ->
                "Aplikasi target tidak tersedia atau telah dicopot."

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
                showCleaningSummary = true,
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

    /**
     * Menutup dialog ringkasan hasil pembersihan.
     */
    fun dismissCleaningSummary() {
        _metricState.value = _metricState.value.copy(showCleaningSummary = false)
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
            _scanState.value = ScanState.Scanning
            repository.getInstalledAppsWithCache().collect { state ->
                _appsState.value = state
                if (state is UiState.Success) {
                    val apps = state.data
                    val measurableApps = apps.filter { it.cacheBytes > 0L }
                    _inventorySummary.value = repository.lastInventorySummary
                    _scanResult.value = repository.lastScanResult

                    val totalCache = repository.calculateTotalCacheFormatted(measurableApps)
                    val totalBytes = measurableApps.sumOf { it.cacheBytes }
                    val unmeasurable = repository.lastInventorySummary.unmeasurablePackagesCount + repository.lastInventorySummary.errorPackagesCount
                    _scanState.value = when {
                        apps.isEmpty() -> ScanState.Empty
                        unmeasurable > 0 -> ScanState.Partial(apps, unmeasurable, totalBytes)
                        else -> ScanState.Success(apps, totalBytes)
                    }
                    _metricState.value = _metricState.value.copy(
                        reclaimableAmount = totalCache,
                        sweepProgress = repository.calculateGaugeProgress(measurableApps),
                        isPurging = false,
                        isCleaning = false,
                        currentCleanIndex = 0,
                        totalCleanApps = 0,
                        currentCleanAppName = null,
                        activeStrategy = null
                    )
                }
                if (state is UiState.PermissionRequired) {
                    _needsPermission.value = true
                    _scanState.value = ScanState.PermissionRequired
                }
                if (state is UiState.Error) {
                    _scanState.value = ScanState.Failed(state.message)
                }
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
