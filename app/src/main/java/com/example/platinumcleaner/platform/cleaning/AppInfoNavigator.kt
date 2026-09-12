package com.example.platinumcleaner.platform.cleaning

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.NavigationResult

/**
 * AppInfoNavigator — Pengelola navigasi andal ke Android Settings App Info (ai_task.md V9 §10, §45, §46, §53).
 *
 * Mengimplementasikan:
 * - Validasi keberadaan paket di PackageManager sesaat sebelum navigasi (§10, §45)
 * - Pemeriksaan status enabled paket (§46)
 * - Resolusi Intent sebelum startActivity (§10)
 * - Logging terstruktur [NAVIGATION] (§49, §81)
 * - Penanganan ActivityNotFoundException secara aman.
 */
object AppInfoNavigator {

    private const val TAG = "AppInfoNavigator"

    /**
     * Membuka halaman Settings detail aplikasi untuk package tertentu.
     *
     * @param context Context untuk startActivity
     * @param packageName Package target yang akan dibuka
     * @return NavigationResult hasil evaluasi dan peluncuran
     */
    fun navigateToAppInfo(context: Context, packageName: String): NavigationResult {
        Log.d(Constants.TAG_CLEAN, "[NAVIGATION] openAppInfo package=$packageName")

        val pm = context.packageManager

        // 1. Validasi keberadaan paket sesaat sebelum navigasi (§10, §45)
        val packageInfo = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(Constants.TAG_CLEAN, "[NAVIGATION] TARGET_UNAVAILABLE — package tidak ditemukan di PM: $packageName")
            return NavigationResult.TARGET_UNAVAILABLE
        }

        // 2. Validasi status aktif/enabled aplikasi (§46)
        if (!packageInfo.applicationInfo.enabled) {
            Log.w(Constants.TAG_CLEAN, "[NAVIGATION] TARGET_UNAVAILABLE — aplikasi dinonaktifkan: $packageName")
            return NavigationResult.TARGET_UNAVAILABLE
        }

        // 3. Bangun Intent Settings ACTION_APPLICATION_DETAILS_SETTINGS (§10)
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 4. Resolusi Intent sebelum peluncuran (§10)
        val resolvedActivity = intent.resolveActivity(pm)
        if (resolvedActivity == null) {
            Log.w(Constants.TAG_CLEAN, "[NAVIGATION] APP_INFO_NOT_OPENED — Intent tidak dapat di-resolve untuk $packageName")
            return NavigationResult.APP_INFO_NOT_OPENED
        }

        Log.d(Constants.TAG_CLEAN, "[NAVIGATION] resolvedActivity=${resolvedActivity.flattenToShortString()} for $packageName")

        // 5. Luncurkan Intent dengan try-catch ActivityNotFoundException (§10)
        return try {
            context.startActivity(intent)
            Log.d(Constants.TAG_CLEAN, "[NAVIGATION] startActivity berhasil untuk $packageName")
            NavigationResult.APP_INFO_OPENED
        } catch (e: ActivityNotFoundException) {
            Log.e(Constants.TAG_CLEAN, "[NAVIGATION] ActivityNotFoundException saat membuka Settings untuk $packageName: ${e.message}")
            NavigationResult.APP_INFO_NOT_OPENED
        } catch (e: SecurityException) {
            Log.e(Constants.TAG_CLEAN, "[NAVIGATION] SecurityException saat membuka Settings untuk $packageName: ${e.message}")
            NavigationResult.APP_INFO_NOT_OPENED
        } catch (e: Exception) {
            Log.e(Constants.TAG_CLEAN, "[NAVIGATION] Error tidak terduga saat membuka Settings untuk $packageName: ${e.message}")
            NavigationResult.APP_INFO_NOT_OPENED
        }
    }
}
