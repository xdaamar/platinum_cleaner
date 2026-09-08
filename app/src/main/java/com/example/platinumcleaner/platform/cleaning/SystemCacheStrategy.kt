package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.platinumcleaner.domain.cleaning.AppCleanResult
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningStrategy
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.domain.verification.VerificationEngine

/**
 * SystemCacheStrategy — Strategy Priority 1 (Best effort).
 *
 * Menggunakan ACTION_CLEAR_APP_CACHE untuk meminta sistem Android
 * menghapus cache aplikasi.
 *
 * PENTING (ai_task.md §12):
 * - Jangan asumsikan behavior-nya tanpa testing di HP fisik
 * - startActivity() BUKAN success cleaning
 * - Success intent ≠ actual cache reduced
 * - Verification wajib via VerificationEngine
 * - Behavior berbeda-beda antar Android version dan OEM
 *
 * Flow:
 * 1. Simpan beforeBytes
 * 2. Launch system-managed intent
 * 3. Return PENDING_VERIFICATION
 * 4. Orchestrator verify setelah app resume
 *
 * Sesuai ai_task.md §9: Priority 1 strategy.
 * Sesuai ai_task.md §34: Tidak melakukan data deletion.
 */
class SystemCacheStrategy : CleaningStrategy {

    override val capability: CleaningCapability = CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST

    private val tag = "SystemCacheStrategy"

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isSystemWideCacheAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(
            tag, "[CLEAN] strategy=SYSTEM_CACHE " +
                    "capability=ACTION_CLEAR_APP_CACHE " +
                    "state=EXECUTING " +
                    "requestId=${request.requestId} " +
                    "targets=${request.targetPackages.size}"
        )

        if (request.targetPackages.isEmpty()) {
            return buildEmptyResult(request)
        }

        return try {
            // Launch system-managed cache clear
            val intent = Intent("android.intent.action.CLEAR_APP_CACHE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            Log.d(tag, "[CLEAN] state=WAITING_FOR_SYSTEM intent launched")

            // Buat pending results untuk semua target
            // Verification akan dilakukan oleh Orchestrator setelah ON_RESUME
            val pendingResults = request.targetPackages.map { packageName ->
                AppCleanResult(
                    packageName = packageName,
                    appName = getAppName(context, packageName),
                    beforeBytes = request.preCleanCacheBytes[packageName] ?: 0L,
                    afterBytes = -1L, // Belum diketahui — menunggu verification
                    status = VerificationStatus.PENDING_VERIFICATION,
                    usedCapability = capability
                )
            }

            CleaningResult(
                requestId = request.requestId,
                appResults = pendingResults,
                selectedCapability = capability,
                overallStatus = VerificationStatus.PENDING_VERIFICATION
            )

        } catch (e: Exception) {
            Log.e(tag, "System cache intent gagal: ${e.message}")

            // Buat failed results
            val failedResults = request.targetPackages.map { packageName ->
                AppCleanResult(
                    packageName = packageName,
                    appName = getAppName(context, packageName),
                    beforeBytes = request.preCleanCacheBytes[packageName] ?: 0L,
                    afterBytes = -1L,
                    status = VerificationStatus.FAILED,
                    usedCapability = capability,
                    errorMessage = e.message
                )
            }

            CleaningResult(
                requestId = request.requestId,
                appResults = failedResults,
                selectedCapability = capability,
                overallStatus = VerificationStatus.FAILED
            )
        }
    }

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
}
