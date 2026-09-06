package com.example.platinumcleaner.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * Helper untuk memeriksa dan mengelola izin khusus (special permissions)
 * yang tidak bisa di-request via dialog runtime biasa.
 */
object PermissionHelper {

    /**
     * Mengecek apakah user sudah memberikan izin PACKAGE_USAGE_STATS
     * (dikenal sebagai "Usage Access" di Settings Android).
     *
     * Izin ini bersifat special permission — tidak bisa di-grant via
     * requestPermissions() biasa. User harus diarahkan manual ke
     * Settings -> Apps -> Special App Access -> Usage Access.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Membuat Intent untuk mengarahkan user ke halaman Usage Access di Settings.
     * Gunakan ini saat hasUsageStatsPermission() mengembalikan false.
     */
    fun buildUsageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
