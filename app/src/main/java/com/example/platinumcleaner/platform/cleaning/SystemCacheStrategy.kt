package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.AppCleanResult
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningStrategy
import com.example.platinumcleaner.domain.cleaning.VerificationStatus

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
 * 1. resolveActivity() cek — apakah intent bisa dihandle?
 * 2. Launch system-managed intent
 * 3. Return PENDING_VERIFICATION (bukan success / bukan fail)
 * 4. Orchestrator verify setelah app resume
 *
 * Sprint 7 fixes:
 * - Gunakan StorageManager.ACTION_CLEAR_APP_CACHE (bukan hardcoded string)
 * - Cek resolveActivity() SEBELUM startActivity() — fail early
 * - Pisahkan ActivityNotFoundException vs SecurityException
 * - Tidak pernah return FAILED hanya karena intent diluncurkan
 * - Context dari caller — tidak modifikasi context type di sini
 *
 * Sesuai ai_task.md §5: Strategy hanya launch + return execution state.
 * Sesuai ai_task.md §6: Distinct failure states.
 */
class SystemCacheStrategy : CleaningStrategy {

    override val capability: CleaningCapability = CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST

    private val tag = "SystemCacheStrategy"

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isSystemWideCacheAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(Constants.TAG_CLEAN, "[STRATEGY] SYSTEM_CACHE executing | requestId=${request.requestId} | targets=${request.targetPackages.size}")
        Log.d(Constants.TAG_CLEAN, "[INTENT] contextClass=${context.javaClass.simpleName}")
        Log.d(tag, "[CLEAN] strategy=SYSTEM_CACHE requestId=${request.requestId} targets=${request.targetPackages.size}")

        if (request.targetPackages.isEmpty()) {
            Log.w(Constants.TAG_CLEAN, "[STRATEGY] targetPackages kosong — return UNKNOWN")
            return buildEmptyResult(request)
        }

        return try {
            // Sprint 7 FIX: Gunakan StorageManager.ACTION_CLEAR_APP_CACHE (API 28+)
            // Ini adalah constant yang benar, bukan "android.intent.action.CLEAR_APP_CACHE"
            val actionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                StorageManager.ACTION_CLEAR_APP_CACHE
            } else {
                // Fallback untuk API < 28 — intent ini tidak ada di versi lama
                // CapabilityResolver sudah memblok API < 28 masuk ke strategy ini
                "android.intent.action.CLEAR_APP_CACHE"
            }

            Log.d(Constants.TAG_CLEAN, "[INTENT] action=\"$actionString\" | apiLevel=${Build.VERSION.SDK_INT}")

            val intent = Intent(actionString).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Sprint 7 FIX: Cek resolveActivity() SEBELUM startActivity()
            // Jika tidak ada handler → return gagal lebih awal dengan pesan yang jelas
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            val resolvedActivity = resolveInfo?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "null"
            Log.d(Constants.TAG_CLEAN, "[INTENT] resolveActivity=$resolvedActivity")

            if (resolveInfo == null) {
                Log.w(Constants.TAG_CLEAN, "[INTENT] INTENT_UNAVAILABLE — tidak ada activity untuk ACTION_CLEAR_APP_CACHE")
                Log.w(tag, "ACTION_CLEAR_APP_CACHE tidak bisa di-resolve — fallback diperlukan")
                return buildIntentUnavailableResult(request)
            }

            // Intent bisa di-resolve — luncurkan
            Log.d(Constants.TAG_CLEAN, "[INTENT] calling startActivity(ACTION_CLEAR_APP_CACHE)")
            context.startActivity(intent)
            Log.d(Constants.TAG_CLEAN, "[INTENT] startActivity() returned normally — system UI should appear")
            Log.d(tag, "[CLEAN] state=WAITING_FOR_SYSTEM intent launched successfully")

            // PENTING: Kita TIDAK tahu apakah cache benar-benar terhapus di sini.
            // Return PENDING_VERIFICATION — Orchestrator verify setelah ON_RESUME.
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

            Log.d(Constants.TAG_CLEAN, "[STRATEGY] returning PENDING_VERIFICATION for ${request.targetPackages.size} packages")

            CleaningResult(
                requestId = request.requestId,
                appResults = pendingResults,
                selectedCapability = capability,
                overallStatus = VerificationStatus.PENDING_VERIFICATION
            )

        } catch (e: android.content.ActivityNotFoundException) {
            // Intent tidak tersedia di perangkat ini (tidak ada activity handler)
            Log.e(Constants.TAG_CLEAN, "[INTENT] ActivityNotFoundException: ${e.message}")
            Log.e(tag, "ActivityNotFoundException — ACTION_CLEAR_APP_CACHE tidak tersedia: ${e.message}")
            buildFailedResult(request, "ActivityNotFoundException: ${e.message}")

        } catch (e: SecurityException) {
            // Blocked oleh OEM, permission, atau SELinux policy
            Log.e(Constants.TAG_CLEAN, "[INTENT] SecurityException: ${e.message} — mungkin blocked OEM/SELinux")
            Log.e(tag, "SecurityException saat launch ACTION_CLEAR_APP_CACHE: ${e.message}")
            buildFailedResult(request, "SecurityException: ${e.message}")

        } catch (e: Exception) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] Exception(${e.javaClass.simpleName}): ${e.message}")
            Log.e(tag, "Error saat launch system cache intent: ${e.message}")
            buildFailedResult(request, "${e.javaClass.simpleName}: ${e.message}")
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

    /**
     * Intent tidak tersedia di perangkat — Orchestrator harus fallback ke Priority 2.
     * Ini bukan FAILED dari user perspective — hanya berarti strategy ini tidak bisa digunakan.
     */
    private fun buildIntentUnavailableResult(request: CleaningRequest) = CleaningResult(
        requestId = request.requestId,
        appResults = request.targetPackages.map { packageName ->
            AppCleanResult(
                packageName = packageName,
                appName = packageName,
                beforeBytes = request.preCleanCacheBytes[packageName] ?: 0L,
                afterBytes = -1L,
                status = VerificationStatus.FAILED,
                usedCapability = capability,
                errorMessage = "ACTION_CLEAR_APP_CACHE tidak tersedia di perangkat ini"
            )
        },
        selectedCapability = capability,
        overallStatus = VerificationStatus.FAILED
    )

    private fun buildFailedResult(request: CleaningRequest, error: String?) = CleaningResult(
        requestId = request.requestId,
        appResults = request.targetPackages.map { packageName ->
            AppCleanResult(
                packageName = packageName,
                appName = packageName,
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
