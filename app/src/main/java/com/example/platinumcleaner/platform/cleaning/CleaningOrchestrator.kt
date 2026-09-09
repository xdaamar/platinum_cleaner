package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.AppCleanResult
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningStrategy
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.domain.verification.VerificationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.platinumcleaner.domain.cleaning.CleaningMode
import com.example.platinumcleaner.domain.cleaning.CleaningPlan

/**
 * CleaningOrchestrator — Brain utama cleaning engine.
 *
 * Tanggung jawab:
 * 1. Terima CleaningRequest / CleaningPlan dari ViewModel
 * 2. Tanya CapabilityResolver → pilih strategy terbaik
 * 3. Eksekusi strategy sesuai mode (System-wide vs Per-App)
 * 4. Jika gagal → fallback ke strategy berikutnya (Priority 1 → 2 → 3)
 * 5. Kembalikan CleaningResult ke ViewModel
 *
 * Sesuai ai_task.md §5: Orchestrator strategy-agnostic.
 * Sesuai ai_task.md §25: Cascading fallback model.
 * Sesuai ai_task.md §30: Pemisahan mode System-wide dan Per-App.
 * Sesuai ai_task.md §54: DILARANG menggunakan ACTION_CLEAR_APP_CACHE per-package.
 */
class CleaningOrchestrator(
    /**
     * Registry strategies yang tersedia, terurut dari prioritas tertinggi.
     */
    private val strategies: List<CleaningStrategy> = listOf(
        SystemCacheStrategy(),
        PerAppIntentStrategy(),
        AccessibilityAutomationStrategy()
    )
) {

    private val tag = "CleaningOrchestrator"

    // ===================================================
    // Core: Execute cleaning plan / request
    // ===================================================

    /**
     * V8: Eksekusi cleaning berdasarkan CleaningPlan eksplisit (§30, §31).
     */
    suspend fun executePlan(
        context: Context,
        plan: CleaningPlan,
        preCleanCacheBytes: Map<String, Long>
    ): CleaningResult {
        Log.d(tag, "[ORCHESTRATOR] Memulai plan mode=${plan.mode} targets=${plan.targets.size} estimatedReclaim=${plan.estimatedReclaim}")

        val preferredCapability = when (plan.mode) {
            CleaningMode.SYSTEM_WIDE -> CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST
            CleaningMode.PER_APP_ASSISTED -> CleaningCapability.PER_APP_NAVIGATION
            CleaningMode.PER_APP_AUTOMATED -> CleaningCapability.ACCESSIBILITY_AUTOMATION
        }

        val request = CleaningRequest(
            targetPackages = plan.targets,
            preCleanCacheBytes = preCleanCacheBytes,
            preferredCapability = preferredCapability
        )

        return execute(context, request)
    }

    /**
     * Eksekusi cleaning request dengan strategy selection dan fallback otomatis.
     *
     * @param context Application context
     * @param request Detail cleaning yang diminta
     * @return CleaningResult dengan status yang jujur
     */
    suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(tag, "[ORCHESTRATOR] Memulai requestId=${request.requestId} targets=${request.targetPackages.size}")

        // Resolve capabilities yang tersedia
        val availableCapabilities = CapabilityResolver.resolve(context)
        Log.d(tag, "[ORCHESTRATOR] Available: ${availableCapabilities.map { it.name }}")

        // Tentukan strategy yang akan digunakan
        val selectedStrategy = selectStrategy(context, request, availableCapabilities)

        if (selectedStrategy == null) {
            Log.w(tag, "[ORCHESTRATOR] Tidak ada strategy yang tersedia — UNSUPPORTED")
            return buildUnsupportedResult(request)
        }

        Log.d(tag, "[ORCHESTRATOR] Strategy terpilih: ${selectedStrategy.capability.name}")

        // Eksekusi strategy utama
        val result = try {
            selectedStrategy.execute(context, request)
        } catch (e: Exception) {
            Log.e(tag, "[ORCHESTRATOR] Strategy ${selectedStrategy.capability.name} exception: ${e.message}")
            null
        }

        // Jika strategy utama gagal total → coba fallback
        if (result == null || result.overallStatus == VerificationStatus.FAILED) {
            Log.w(tag, "[ORCHESTRATOR] Strategy gagal — mencari fallback...")
            val fallbackResult = tryFallback(context, request, selectedStrategy.capability, availableCapabilities)
            if (fallbackResult != null) return fallbackResult
        }

        return result ?: buildFailedResult(request, selectedStrategy.capability)
    }

    /**
     * Verifikasi hasil cleaning setelah user kembali dari Settings (ON_RESUME).
     *
     * Dipanggil oleh ViewModel saat menerima lifecycle ON_RESUME event.
     * Mengambil cache size terbaru dan membandingkan dengan beforeBytes.
     *
     * @param context Application context
     * @param pendingResult Hasil PENDING_VERIFICATION dari execute()
     * @return CleaningResult yang sudah diverifikasi
     */
    suspend fun verifyAfterResume(
        context: Context,
        pendingResult: CleaningResult
    ): CleaningResult = withContext(Dispatchers.IO) {
        Log.d(tag, "[ORCHESTRATOR] Verifying after resume requestId=${pendingResult.requestId}")
        Log.d(Constants.TAG_CLEAN, "[VERIFICATION] verifyAfterResume | packages=${pendingResult.appResults.map { it.packageName }}")

        val verifiedAppResults = pendingResult.appResults.map { appResult ->
            if (appResult.status != VerificationStatus.PENDING_VERIFICATION) {
                Log.d(Constants.TAG_CLEAN, "[VERIFICATION] ${appResult.packageName} status=${appResult.status} — skip re-verify")
                return@map appResult // Sudah ada status — tidak perlu re-verify
            }

            Log.d(Constants.TAG_CLEAN, "[VERIFICATION] ${appResult.packageName} | beforeBytes=${VerificationEngine.formatBytes(appResult.beforeBytes)} | starting staged verify")

            // Sprint 7: Gunakan staged verify (T0→T4), fresh query, bounded 5000ms
            val (afterBytes, status) = VerificationEngine.verify(
                context = context,
                packageName = appResult.packageName,
                beforeBytes = appResult.beforeBytes
            )

            Log.d(
                tag,
                "[VERIFY] ${appResult.packageName} | " +
                        "before=${VerificationEngine.formatBytes(appResult.beforeBytes)} " +
                        "after=${VerificationEngine.formatBytes(afterBytes)} " +
                        "result=$status"
            )
            Log.d(
                Constants.TAG_CLEAN,
                "[VERIFICATION] ${appResult.packageName} DONE | " +
                        "before=${VerificationEngine.formatBytes(appResult.beforeBytes)} | " +
                        "after=${VerificationEngine.formatBytes(afterBytes)} | " +
                        "reclaimed=${VerificationEngine.formatBytes(maxOf(0L, appResult.beforeBytes - afterBytes))} | " +
                        "status=$status"
            )

            appResult.copy(afterBytes = afterBytes, status = status)
        }

        // Hitung overall status dari semua app
        val overallStatus = computeOverallStatus(verifiedAppResults)
        Log.d(Constants.TAG_CLEAN, "[RESULT] verifyAfterResume complete | overallStatus=$overallStatus | totalReclaimed=${VerificationEngine.formatBytes(verifiedAppResults.sumOf { maxOf(0L, it.beforeBytes - it.afterBytes) })}")

        pendingResult.copy(
            appResults = verifiedAppResults,
            overallStatus = overallStatus
        )
    }


    // ===================================================
    // Strategy Selection
    // ===================================================

    private fun selectStrategy(
        context: Context,
        request: CleaningRequest,
        availableCapabilities: List<CleaningCapability>
    ): CleaningStrategy? {
        // Jika ada preferensi spesifik, gunakan itu
        if (request.preferredCapability != null) {
            val preferred = strategies.find {
                it.capability == request.preferredCapability && it.isSupported(context)
            }
            if (preferred != null) return preferred
            Log.w(tag, "Preferred strategy ${request.preferredCapability} tidak tersedia — auto-select")
        }

        // V8 FIX (§54): Jika request ditujukan ke 1 package spesifik (targeted clean):
        // DILARANG memilih SYSTEM_WIDE_CACHE_REQUEST!
        val isTargetedSingleApp = request.targetPackages.size == 1
        val candidateCapabilities = if (isTargetedSingleApp) {
            availableCapabilities.filter { it != CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST }
        } else {
            availableCapabilities
        }

        // Auto-select berdasarkan priority order dari CapabilityResolver
        for (capability in candidateCapabilities) {
            val strategy = strategies.find {
                it.capability == capability && it.isSupported(context)
            }
            if (strategy != null) return strategy
        }

        return null
    }

    // ===================================================
    // Fallback Logic
    // ===================================================

    /**
     * Coba strategy berikutnya jika strategy utama gagal.
     * Sesuai ai_task.md §25: Fallback cascade.
     */
    private suspend fun tryFallback(
        context: Context,
        request: CleaningRequest,
        failedCapability: CleaningCapability,
        availableCapabilities: List<CleaningCapability>
    ): CleaningResult? {
        val isTargetedSingleApp = request.targetPackages.size == 1
        val remainingCapabilities = availableCapabilities
            .filter { it != failedCapability && it != CleaningCapability.UNSUPPORTED }
            .filter { !isTargetedSingleApp || it != CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST }
            .sortedBy { it.priority }

        for (capability in remainingCapabilities) {
            val fallbackStrategy = strategies.find {
                it.capability == capability && it.isSupported(context)
            } ?: continue

            Log.d(tag, "[ORCHESTRATOR] Mencoba fallback: ${capability.name}")
            return try {
                fallbackStrategy.execute(context, request)
            } catch (e: Exception) {
                Log.e(tag, "[ORCHESTRATOR] Fallback ${capability.name} gagal: ${e.message}")
                null
            }
        }

        Log.w(tag, "[ORCHESTRATOR] Semua fallback habis — tidak ada strategy tersisa")
        return null
    }

    // ===================================================
    // Result Helpers
    // ===================================================

    private fun computeOverallStatus(results: List<AppCleanResult>): VerificationStatus {
        if (results.isEmpty()) return VerificationStatus.UNKNOWN

        val hasVerifiedSuccess = results.any { it.status == VerificationStatus.VERIFIED_SUCCESS }
        val hasVerifiedPartial = results.any { it.status == VerificationStatus.VERIFIED_PARTIAL }
        val hasLegacySuccess = results.any { it.status == VerificationStatus.PARTIAL_SUCCESS } // backward compat
        val allFailed = results.all { it.status == VerificationStatus.FAILED || it.status == VerificationStatus.INTENT_LAUNCH_FAILED }
        val allNoChange = results.all { it.status == VerificationStatus.NO_CHANGE || it.status == VerificationStatus.NO_MEASURABLE_CHANGE }
        val allCancelled = results.all { it.status == VerificationStatus.USER_CANCELLED }
        val hasPending = results.any { it.status == VerificationStatus.PENDING_VERIFICATION }
        val allTimeout = results.all { it.status == VerificationStatus.VERIFICATION_TIMEOUT }

        return when {
            hasPending -> VerificationStatus.PENDING_VERIFICATION
            allCancelled -> VerificationStatus.USER_CANCELLED
            allTimeout -> VerificationStatus.VERIFICATION_TIMEOUT
            allFailed -> VerificationStatus.FAILED
            allNoChange -> VerificationStatus.NO_MEASURABLE_CHANGE
            hasVerifiedSuccess && !hasVerifiedPartial && !hasLegacySuccess ->
                VerificationStatus.VERIFIED_SUCCESS
            hasVerifiedSuccess || hasVerifiedPartial || hasLegacySuccess ->
                VerificationStatus.VERIFIED_PARTIAL
            else -> VerificationStatus.UNKNOWN
        }
    }

    private fun buildUnsupportedResult(request: CleaningRequest) = CleaningResult(
        requestId = request.requestId,
        appResults = request.targetPackages.map {
            AppCleanResult(
                packageName = it,
                appName = it,
                beforeBytes = request.preCleanCacheBytes[it] ?: 0L,
                afterBytes = -1L,
                status = VerificationStatus.FAILED,
                usedCapability = CleaningCapability.UNSUPPORTED,
                errorMessage = "No cleaning capability available on this device"
            )
        },
        selectedCapability = CleaningCapability.UNSUPPORTED,
        overallStatus = VerificationStatus.FAILED
    )

    private fun buildFailedResult(
        request: CleaningRequest,
        capability: CleaningCapability
    ) = CleaningResult(
        requestId = request.requestId,
        appResults = request.targetPackages.map {
            AppCleanResult(
                packageName = it,
                appName = it,
                beforeBytes = request.preCleanCacheBytes[it] ?: 0L,
                afterBytes = -1L,
                status = VerificationStatus.FAILED,
                usedCapability = capability,
                errorMessage = "Strategy execution failed"
            )
        },
        selectedCapability = capability,
        overallStatus = VerificationStatus.FAILED
    )
}
