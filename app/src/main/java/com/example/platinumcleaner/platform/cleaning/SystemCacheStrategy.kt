package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.platinumcleaner.Constants
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
 *
 * Sprint 7: Tambah structured logging [INTENT] untuk diagnosa eksekusi fisik.
 */
class SystemCacheStrategy : CleaningStrategy {

    override val capability: CleaningCapability = CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST

    private val tag = "SystemCacheStrategy"

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isSystemWideCacheAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(
            Constants.TAG_CLEAN, "[STRATEGY] strategy=SYSTEM_CACHE " +
                    "capability=ACTION_CLEAR_APP_CACHE " +
                    "state=EXECUTING " +
                    "requestId=${request.requestId} " +
                    "targets=${request.targetPackages.size}"
        )
        Log.d(tag, "[CLEAN] strategy=SYSTEM_CACHE requestId=${request.requestId} targets=${request.targetPackages.size}")

        // Log context type — penting untuk diagnosa crash dari Application context
        Log.d(Constants.TAG_CLEAN, "[INTENT] contextClass=${context.javaClass.simpleName} (must be Activity or Application with FLAG_ACTIVITY_NEW_TASK)")

        if (request.targetPackages.isEmpty()) {
            Log.w(Constants.TAG_CLEAN, "[STRATEGY] targetPackages kosong — return UNKNOWN")
            return buildEmptyResult(request)
        }

        return try {
            // Sprint 7 diagnostic: log action string dan resolve info SEBELUM launch
            val actionString = "android.intent.action.CLEAR_APP_CACHE"
            val intent = Intent(actionString).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Cek resolveActivity — apakah ada handler?
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            Log.d(Constants.TAG_CLEAN, "[INTENT] action=\"$actionString\" resolveActivity=${resolveInfo?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "null (UNRESOLVABLE)"}")

            if (resolveInfo == null) {
                Log.w(Constants.TAG_CLEAN, "[INTENT] INTENT_UNAVAILABLE — tidak ada activity yang bisa handle ACTION_CLEAR_APP_CACHE")
            }

            // Launch intent
            Log.d(Constants.TAG_CLEAN, "[INTENT] calling startActivity()")
            context.startActivity(intent)
            Log.d(Constants.TAG_CLEAN, "[INTENT] startActivity() returned (tidak crash)")
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

            Log.d(Constants.TAG_CLEAN, "[STRATEGY] result=PENDING_VERIFICATION packages=${request.targetPackages}")

            CleaningResult(
                requestId = request.requestId,
                appResults = pendingResults,
                selectedCapability = capability,
                overallStatus = VerificationStatus.PENDING_VERIFICATION
            )

        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] ActivityNotFoundException: ${e.message} — ACTION_CLEAR_APP_CACHE tidak tersedia di perangkat ini")
            Log.e(tag, "System cache intent ActivityNotFoundException: ${e.message}")
            buildFailedResult(request, "ActivityNotFoundException: ${e.message}")

        } catch (e: SecurityException) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] SecurityException: ${e.message} — mungkin membutuhkan permission tambahan atau blocked oleh OEM")
            Log.e(tag, "System cache intent SecurityException: ${e.message}")
            buildFailedResult(request, "SecurityException: ${e.message}")

        } catch (e: Exception) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] Exception(${e.javaClass.simpleName}): ${e.message}")
            Log.e(tag, "System cache intent gagal: ${e.message}")
            buildFailedResult(request, e.message)
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

    private fun buildFailedResult(request: CleaningRequest, error: String?) = CleaningResult(
        requestId = request.requestId,
        appResults = request.targetPackages.map { packageName ->
            AppCleanResult(
                packageName = packageName,
                appName = packageName, // Simplified — avoid context leak in error path
                beforeBytes = request.preCleanCacheBytes[packageName] ?: 0L,
                afterBytes = -1L,
                status = VerificationStatus.FAILED,
                usedCapability = capability,
                errorMessage = error
            )
        },
        selectedCapability = capability,
        overallStatus = VerificationStatus.FAILED
    )
}
