package com.example.platinumcleaner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.platinumcleaner.Constants

/**
 * OverlayPermissionHelper — Utility izin SYSTEM_ALERT_WINDOW (Display over other apps).
 *
 * Diperlukan untuk menampilkan Floating Assistant Bar di atas halaman Pengaturan Android.
 */
object OverlayPermissionHelper {

    private const val TAG = "OverlayPermissionHelper"

    /**
     * Memeriksa apakah aplikasi memiliki izin menampilkan overlay di atas aplikasi lain.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Settings.canDrawOverlays(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking overlay permission: ${e.message}")
                false
            }
        } else {
            true
        }
    }

    /**
     * Membuat Intent untuk membuka halaman pengaturan izin Overlay aplikasi ini.
     */
    fun createOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
