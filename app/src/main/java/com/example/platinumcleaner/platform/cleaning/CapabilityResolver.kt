package com.example.platinumcleaner.platform.cleaning

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.util.PermissionHelper

/**
 * CapabilityResolver — Mendeteksi cleaning capability yang benar-benar
 * tersedia pada perangkat saat ini.
 *
 * Sesuai ai_task.md §7: Hanya MENDETEKSI, TIDAK mengeksekusi.
 * Sesuai ai_task.md §4: Capability-based, bukan device-model-based.
 * Sesuai ai_task.md §9: System > Per-App > Accessibility (priority order).
 *
 * Hasilnya adalah List<CleaningCapability> terurut dari prioritas tertinggi,
 * bukan Boolean tunggal.
 */
object CapabilityResolver {

    private const val TAG = "CapabilityResolver"

    /**
     * Deteksi semua capability yang tersedia pada perangkat saat ini.
     *
     * @return List terurut dari capability yang available (prioritas tertinggi dulu).
     *         Selalu mengandung minimal PER_APP_NAVIGATION.
     */
    fun resolve(context: Context): List<CleaningCapability> {
        val available = mutableListOf<CleaningCapability>()

        // Priority 1: System-wide cache request
        if (isSystemWideCacheAvailable(context)) {
            available.add(CleaningCapability.SYSTEM_WIDE_CACHE_REQUEST)
            Log.d(TAG, "[CAPABILITY] SYSTEM_WIDE_CACHE_REQUEST: tersedia")
        } else {
            Log.d(TAG, "[CAPABILITY] SYSTEM_WIDE_CACHE_REQUEST: tidak tersedia")
        }

        // Priority 2: Per-app navigation — selalu tersedia sebagai fallback minimum
        if (isPerAppNavigationAvailable(context)) {
            available.add(CleaningCapability.PER_APP_NAVIGATION)
            Log.d(TAG, "[CAPABILITY] PER_APP_NAVIGATION: tersedia")
        }

        // Priority 3: Accessibility automation (optional, user-enabled)
        if (isAccessibilityAutomationAvailable(context)) {
            available.add(CleaningCapability.ACCESSIBILITY_AUTOMATION)
            Log.d(TAG, "[CAPABILITY] ACCESSIBILITY_AUTOMATION: aktif")
        } else {
            Log.d(TAG, "[CAPABILITY] ACCESSIBILITY_AUTOMATION: tidak aktif")
        }

        if (available.isEmpty()) {
            available.add(CleaningCapability.UNSUPPORTED)
            Log.w(TAG, "[CAPABILITY] Tidak ada capability yang tersedia!")
        }

        Log.d(TAG, "[CAPABILITY] Resolved: ${available.map { it.name }}")
        return available
    }

    /**
     * Ambil capability terbaik yang tersedia (priority 1).
     */
    fun resolveBest(context: Context): CleaningCapability {
        return resolve(context).first()
    }

    // ===================================================
    // Individual capability checks
    // ===================================================

    /**
     * Cek apakah ACTION_CLEAR_APP_CACHE intent tersedia pada perangkat ini.
     *
     * Catatan penting (ai_task.md §12):
     * - Jangan asumsikan behavior-nya tanpa testing
     * - Ketersediaan intent ≠ jaminan silent deletion
     * - Verification tetap wajib setelah eksekusi
     */
    fun isSystemWideCacheAvailable(context: Context): Boolean {
        return try {
            // Cek apakah ada activity yang bisa handle intent ini
            val intent = Intent("android.intent.action.CLEAR_APP_CACHE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            val isAvailable = resolveInfo != null
            Log.d(TAG, "ACTION_CLEAR_APP_CACHE resolvable: $isAvailable")
            isAvailable
        } catch (e: Exception) {
            Log.w(TAG, "Error cek system cache capability: ${e.message}")
            false
        }
    }

    /**
     * Cek apakah per-app Settings navigation tersedia.
     * Ini adalah fallback minimum — hampir selalu true di Android 5+.
     */
    fun isPerAppNavigationAvailable(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo != null
        } catch (e: Exception) {
            Log.w(TAG, "Error cek per-app navigation: ${e.message}")
            true // Assume available jika tidak bisa dicek
        }
    }

    /**
     * Cek apakah Accessibility automation tersedia DAN aktif oleh user.
     *
     * Sesuai ai_task.md §10: Accessibility OPTIONAL.
     * Hanya tersedia jika user secara eksplisit mengaktifkan service.
     */
    fun isAccessibilityAutomationAvailable(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? AccessibilityManager ?: return false

            // Cek apakah PlatinumCleanerService aktif
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            enabledServices.any { serviceInfo ->
                serviceInfo.resolveInfo.serviceInfo.packageName == Constants.OUR_PACKAGE_NAME
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cek accessibility: ${e.message}")
            false
        }
    }

    /**
     * Cek apakah Usage Access permission sudah diberikan.
     * Diperlukan oleh Scanner untuk membaca cache statistics.
     */
    fun hasUsageAccess(context: Context): Boolean {
        return PermissionHelper.hasUsageStatsPermission(context)
    }
}
