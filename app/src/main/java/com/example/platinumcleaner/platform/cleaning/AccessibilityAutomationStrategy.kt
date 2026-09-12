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
import com.example.platinumcleaner.domain.cleaning.NavigationResult
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

        val appName = getAppName(context, targetPackage)
        val navResult = AppInfoNavigator.navigateToAppInfo(context, targetPackage)

        return when (navResult) {
            NavigationResult.APP_INFO_OPENED -> {
                val appResult = AppCleanResult(
                    packageName = targetPackage,
                    appName = appName,
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
            }
            NavigationResult.TARGET_UNAVAILABLE -> {
                val appResult = AppCleanResult(
                    packageName = targetPackage,
                    appName = appName,
                    beforeBytes = beforeBytes,
                    afterBytes = -1L,
                    status = VerificationStatus.TARGET_UNAVAILABLE,
                    usedCapability = capability,
                    errorMessage = "Aplikasi target tidak tersedia atau dinonaktifkan"
                )
                CleaningResult(
                    requestId = request.requestId,
                    appResults = listOf(appResult),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.TARGET_UNAVAILABLE
                )
            }
            NavigationResult.APP_INFO_NOT_OPENED -> {
                val appResult = AppCleanResult(
                    packageName = targetPackage,
                    appName = appName,
                    beforeBytes = beforeBytes,
                    afterBytes = -1L,
                    status = VerificationStatus.INTENT_UNAVAILABLE,
                    usedCapability = capability,
                    errorMessage = "Halaman detail aplikasi tidak tersedia"
                )
                CleaningResult(
                    requestId = request.requestId,
                    appResults = listOf(appResult),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.INTENT_UNAVAILABLE
                )
            }
            else -> {
                CleaningResult(
                    requestId = request.requestId,
                    appResults = emptyList(),
                    selectedCapability = capability,
                    overallStatus = VerificationStatus.NAVIGATION_FAILED
                )
            }
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
