package com.example.platinumcleaner.domain.verification

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import java.util.Locale
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VerificationEngine — Memverifikasi hasil cleaning dengan membandingkan
 * ukuran cache sebelum dan sesudah.
 *
 * Sesuai ai_task.md §14 (Verification Engine):
 * BEFORE → ACTION → WAIT/RESUME → AFTER → COMPARE
 *
 * Sesuai ai_task.md §15 (Verification Latency):
 * Tidak hardcode delay tanpa alasan. Gunakan bounded retry dengan timeout.
 *
 * Sesuai ai_task.md §27 (Observability):
 * Log structured: [VERIFY] before/after/reclaimed/result
 * TIDAK ADA network telemetry.
 */
object VerificationEngine {

    private const val TAG = "VerificationEngine"

    /**
     * Threshold minimum agar dianggap VERIFIED_SUCCESS.
     * Jika cache berkurang >= 90% dari before, dianggap berhasil penuh.
     * Jika berkurang 10-89%, dianggap PARTIAL_SUCCESS.
     * Di bawah 10% = NO_CHANGE (mungkin sistem memerlukan waktu lebih lama).
     */
    private const val SUCCESS_THRESHOLD = 0.90f
    private const val PARTIAL_THRESHOLD = 0.10f

    /**
     * Query cache size aktual dari perangkat untuk satu package.
     * WAJIB di Dispatchers.IO — StorageStatsManager adalah I/O operation.
     *
     * @return cache bytes saat ini, atau -1L jika tidak bisa di-query
     */
    suspend fun queryCacheBytes(context: Context, packageName: String): Long =
        withContext(Dispatchers.IO) {
            try {
                val storageStatsManager =
                    context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val packageManager = context.packageManager
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageName,
                    UserHandle.getUserHandleForUid(appInfo.uid)
                )
                stats.cacheBytes
            } catch (e: Exception) {
                Log.w(TAG, "Tidak bisa query cache untuk $packageName: ${e.message}")
                -1L
            }
        }

    /**
     * Verifikasi hasil cleaning satu app dengan bounded retry.
     *
     * Tidak langsung percaya angka pertama — sistem Android butuh beberapa
     * saat untuk mempropagasi perubahan cache ke StorageStatsManager.
     * Sesuai ai_task.md §15: event-driven, bukan fixed delay.
     *
     * @param context Application context
     * @param packageName Package name yang di-verify
     * @param beforeBytes Cache size sebelum cleaning
     * @param maxRetries Berapa kali retry jika angka tidak berubah
     * @param retryDelayMs Delay antar retry (ms) — bounded, bukan infinite
     * @return VerificationStatus yang merepresentasikan hasil nyata
     */
    suspend fun verify(
        context: Context,
        packageName: String,
        beforeBytes: Long,
        maxRetries: Int = 3,
        retryDelayMs: Long = 1_500L
    ): Pair<Long, VerificationStatus> = withContext(Dispatchers.IO) {
        if (beforeBytes <= 0L) {
            Log.w(TAG, "[$packageName] beforeBytes tidak valid ($beforeBytes) — UNKNOWN")
            return@withContext Pair(-1L, VerificationStatus.UNKNOWN)
        }

        var afterBytes = -1L
        var status = VerificationStatus.PENDING_VERIFICATION

        // Bounded retry — tidak infinite
        repeat(maxRetries) { attempt ->
            kotlinx.coroutines.delay(retryDelayMs)
            val current = queryCacheBytes(context, packageName)

            if (current < 0L) {
                Log.w(TAG, "[$packageName] Gagal query attempt ${attempt + 1}")
                return@repeat
            }

            afterBytes = current
            status = classify(beforeBytes, afterBytes)

            Log.d(
                TAG, "[VERIFY] $packageName | " +
                        "before=${formatBytes(beforeBytes)} " +
                        "after=${formatBytes(afterBytes)} " +
                        "reclaimed=${formatBytes(maxOf(0L, beforeBytes - afterBytes))} " +
                        "result=$status (attempt ${attempt + 1}/$maxRetries)"
            )

            // Jika sudah ada perubahan, tidak perlu retry lagi
            if (status != VerificationStatus.NO_CHANGE &&
                status != VerificationStatus.PENDING_VERIFICATION
            ) {
                return@withContext Pair(afterBytes, status)
            }
        }

        Pair(afterBytes, status)
    }

    /**
     * Mengklasifikasikan hasil berdasarkan perbandingan before/after.
     */
    fun classify(beforeBytes: Long, afterBytes: Long): VerificationStatus {
        if (afterBytes < 0L) return VerificationStatus.UNKNOWN
        if (beforeBytes <= 0L) return VerificationStatus.UNKNOWN

        val reduction = beforeBytes - afterBytes
        if (reduction <= 0L) return VerificationStatus.NO_CHANGE

        val ratio = reduction.toFloat() / beforeBytes.toFloat()
        return when {
            ratio >= SUCCESS_THRESHOLD -> VerificationStatus.VERIFIED_SUCCESS
            ratio >= PARTIAL_THRESHOLD -> VerificationStatus.PARTIAL_SUCCESS
            else -> VerificationStatus.NO_CHANGE
        }
    }

    /**
     * Format bytes ke string yang mudah dibaca manusia.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "N/A"
        return when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
            bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Format bytes ke string "Up to X potentially reclaimable" — honest wording.
     * Sesuai ai_task.md §16: jangan janjikan angka pasti.
     */
    fun formatReclaimableEstimate(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return "Up to ${formatBytes(bytes)} potentially reclaimable"
    }

    /**
     * Format reclaimed bytes ke string hasil verifikasi.
     * Sesuai ai_task.md §16: hanya tampilkan angka jika verification mendukung.
     */
    fun formatReclaimedVerified(bytes: Long): String {
        if (bytes <= 0L) return "No change detected"
        return "Reclaimed ${formatBytes(bytes)}"
    }
}
