package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.AppCleanResult
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningRequest
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningStrategy
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import com.example.platinumcleaner.service.CleanSessionManager

/**
 * AccessibilityAutomationStrategy — Strategy Priority 3 (Optional Automation).
 *
 * Sesuai ai_task.md §19 & §28 & §29:
 * - Hanya aktif jika user secara eksplisit mengaktifkan Accessibility Service.
 * - Membuka App Info dan mempercayakan klik pada AccessibilityService yang terisolasi.
 * - Jika gagal atau service mati -> fallback ke PerAppAssistedStrategy (§52).
 */
class AccessibilityAutomationStrategy : CleaningStrategy {

    override val capability: CleaningCapability = CleaningCapability.ACCESSIBILITY_AUTOMATION

    private val tag = "AccessibilityAutomationStrategy"

    override fun isSupported(context: Context): Boolean {
        return CapabilityResolver.isAccessibilityAutomationAvailable(context)
    }

    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult {
        Log.d(tag, "[CLEAN] strategy=ACCESSIBILITY_AUTOMATION requestId=${request.requestId}")

        if (request.targetPackages.isEmpty()) {
            return CleaningResult(
                requestId = request.requestId,
                appResults = emptyList(),
                selectedCapability = capability,
                overallStatus = VerificationStatus.UNKNOWN
            )
        }

        val targetPackage = request.targetPackages.first()
        val beforeBytes = request.preCleanCacheBytes[targetPackage] ?: 0L

        return try {
            CleanSessionManager.startSession(targetPackage)
            CleanSessionManager.markExecuting(capability)

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", targetPackage, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) == null) {
                Log.w(tag, "Intent detail settings tidak dapat di-resolve untuk $targetPackage")
                return CleaningResult(
                    requestId = request.requestId,
                    appResults = emptyList(),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.INTENT_UNAVAILABLE
                )
            }

            context.startActivity(intent)
            Log.d(tag, "Navigating to Settings with Accessibility automation for: $targetPackage")

            val appResult = AppCleanResult(
                packageName = targetPackage,
                appName = getAppName(context, targetPackage),
                beforeBytes = beforeBytes,
                afterBytes = -1L,
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
            Log.e(tag, "Error saat eksekusi otomasi accessibility untuk $targetPackage: ${e.message}")
            CleaningResult(
                requestId = request.requestId,
                appResults = emptyList(),
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
}
