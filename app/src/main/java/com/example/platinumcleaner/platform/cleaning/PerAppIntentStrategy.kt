package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.platinumcleaner.domain.cleaning.AppCleanResult
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningStrategy
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.domain.verification.VerificationEngine

/**
 * PerAppIntentStrategy — Strategy Priority 2 (Fallback).
 *
 * Membuka halaman Settings per-app via ACTION_APPLICATION_DETAILS_SETTINGS.
 * User diarahkan ke halaman yang tepat dan diminta klik Clear Cache secara manual.
 *
 * Flow:
 * 1. Simpan beforeBytes dari request
 * 2. Buka Settings App Detail untuk app pertama dalam queue
 * 3. Status langsung PENDING_VERIFICATION (user perlu aksi manual)
 * 4. Verification terjadi saat app resume (di-handle oleh Orchestrator via ON_RESUME)
 *
 * Sesuai ai_task.md §13: Pertahankan ACTION_APPLICATION_DETAILS_SETTINGS sebagai fallback.
 * Sesuai ai_task.md §34: Semantics spesifik — navigateToCacheSettings, bukan clearApp.
 */
class PerAppIntentStrategy : CleaningStrategy {

    override val capability: CleaningCapability = CleaningCapability.PER_APP_NAVIGATION

    private val tag = "PerAppIntentStrategy"

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isPerAppNavigationAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(tag, "[CLEAN] strategy=PER_APP_INTENT capability=PER_APP_NAVIGATION requestId=${request.requestId}")

        if (request.targetPackages.isEmpty()) {
            return buildEmptyResult(request)
        }

        // Untuk per-app strategy: proses app terarah (yang pertama dalam queue)
        val targetPackage = request.targetPackages.first()
        val beforeBytes = request.preCleanCacheBytes[targetPackage] ?: 0L

        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", targetPackage, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // V8 FIX (§24): Validasi resolveActivity sebelum startActivity
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.w(tag, "[INTENT] INTENT_UNAVAILABLE — Settings App Details tidak tersedia untuk $targetPackage")
                return CleaningResult(
                    requestId = request.requestId,
                    appResults = listOf(
                        AppCleanResult(
                            packageName = targetPackage,
                            appName = getAppName(context, targetPackage),
                            beforeBytes = beforeBytes,
                            afterBytes = -1L,
                            status = VerificationStatus.INTENT_UNAVAILABLE,
                            usedCapability = capability,
                            errorMessage = "Halaman detail aplikasi tidak tersedia"
                        )
                    ),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.INTENT_UNAVAILABLE
                )
            }

            context.startActivity(intent)
            Log.d(tag, "[INTENT] Navigating to Settings for: $targetPackage")
            Log.d(tag, "[CLEAN] state=WAITING_FOR_USER package=$targetPackage")

            val appResult = AppCleanResult(
                packageName = targetPackage,
                appName = getAppName(context, targetPackage),
                beforeBytes = beforeBytes,
                afterBytes = -1L, // Belum diketahui sebelum verifikasi
                status = VerificationStatus.PENDING_VERIFICATION,
                usedCapability = capability
            )

            CleaningResult(
                requestId = request.requestId,
                appResults = listOf(appResult),
                selectedCapability = capability,
                overallStatus = VerificationStatus.PENDING_VERIFICATION
            )

        } catch (e: Exception) {
            Log.e(tag, "Error membuka Settings untuk $targetPackage: ${e.message}")
            buildFailedResult(request, targetPackage, beforeBytes, e.message)
        }
    }

    // ===================================================
    // Helpers
    // ===================================================

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun buildEmptyResult(request: CleaningRequest) = CleaningResult(
        requestId = request.requestId,
        appResults = emptyList(),
        selectedCapability = capability,
        overallStatus = VerificationStatus.UNKNOWN
    )

    private fun buildFailedResult(
        request: CleaningRequest,
        packageName: String,
        beforeBytes: Long,
        error: String?
    ) = CleaningResult(
        requestId = request.requestId,
        appResults = listOf(
            AppCleanResult(
                packageName = packageName,
                appName = packageName,
                beforeBytes = beforeBytes,
                afterBytes = -1L,
                status = VerificationStatus.INTENT_LAUNCH_FAILED,
                usedCapability = capability,
                errorMessage = error
            )
        ),
        selectedCapability = capability,
        overallStatus = VerificationStatus.INTENT_LAUNCH_FAILED
    )
}
