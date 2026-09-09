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

    companion object {
        const val SYSTEM_TARGET_PACKAGE = "system_wide"
    }

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isSystemWideCacheAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        val totalBeforeBytes = request.preCleanCacheBytes.values.sum()
        Log.d(Constants.TAG_CLEAN, "[STRATEGY] SYSTEM_WIDE executing | requestId=${request.requestId} | totalBeforeBytes=$totalBeforeBytes")
        Log.d(Constants.TAG_CLEAN, "[INTENT] contextClass=${context.javaClass.simpleName}")

        return try {
            // V8 FIX: Gunakan StorageManager.ACTION_CLEAR_APP_CACHE (API 28+)
            val actionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                StorageManager.ACTION_CLEAR_APP_CACHE
            } else {
                "android.intent.action.CLEAR_APP_CACHE"
            }

            Log.d(Constants.TAG_CLEAN, "[INTENT] action=\"$actionString\" | apiLevel=${Build.VERSION.SDK_INT}")

            val intent = Intent(actionString).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            val resolvedActivity = resolveInfo?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "null"
            Log.d(Constants.TAG_CLEAN, "[INTENT] resolveActivity=$resolvedActivity")

            if (resolveInfo == null) {
                Log.w(Constants.TAG_CLEAN, "[INTENT] INTENT_UNAVAILABLE — tidak ada activity untuk ACTION_CLEAR_APP_CACHE")
                return CleaningResult(
                    requestId = request.requestId,
                    appResults = emptyList(),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.INTENT_UNAVAILABLE
                )
            }

            // Intent bisa di-resolve — luncurkan sistem Android
            Log.d(Constants.TAG_CLEAN, "[INTENT] calling startActivity(ACTION_CLEAR_APP_CACHE)")
            context.startActivity(intent)
            Log.d(Constants.TAG_CLEAN, "[INTENT] startActivity() returned — system UI should appear")

            // V8 FIX (§18, §19): Satu operasi sistem-level — BUKAN pura-pura antri per aplikasi
            val systemResult = AppCleanResult(
                packageName = SYSTEM_TARGET_PACKAGE,
                appName = "Penyimpanan Sistem Android",
                beforeBytes = totalBeforeBytes,
                afterBytes = -1L,
                status = VerificationStatus.WAITING_FOR_SYSTEM_ACTION,
                usedCapability = capability
            )

            CleaningResult(
                requestId = request.requestId,
                appResults = listOf(systemResult),
                selectedCapability = capability,
                overallStatus = VerificationStatus.WAITING_FOR_SYSTEM_ACTION
            )

        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] ActivityNotFoundException: ${e.message}")
            CleaningResult(
                requestId = request.requestId,
                appResults = emptyList(),
                selectedCapability = capability,
                overallStatus = VerificationStatus.INTENT_UNAVAILABLE
            )
        } catch (e: SecurityException) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] SecurityException: ${e.message}")
            CleaningResult(
                requestId = request.requestId,
                appResults = emptyList(),
                selectedCapability = capability,
                overallStatus = VerificationStatus.INTENT_LAUNCH_FAILED
            )
        } catch (e: Exception) {
            Log.e(Constants.TAG_CLEAN, "[INTENT] Exception(${e.javaClass.simpleName}): ${e.message}")
            CleaningResult(
                requestId = request.requestId,
                appResults = emptyList(),
                selectedCapability = capability,
                overallStatus = VerificationStatus.FAILED
            )
        }
    }
}
