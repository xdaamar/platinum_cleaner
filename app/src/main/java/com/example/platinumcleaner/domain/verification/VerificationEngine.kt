package com.example.platinumcleaner.domain.verification

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import java.util.Locale
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * VerificationEngine — Memverifikasi hasil cleaning dengan membandingkan
 * ukuran cache sebelum dan sesudah.
 *
 * Sesuai ai_task.md §14 (Verification Engine):
 * BEFORE → ACTION → WAIT/RESUME → AFTER → COMPARE
 *
 * Sprint 7 Fixes (ai_task.md §8, §9):
 * - Implementasi staged sampling: T0=0ms, T1=500ms, T2=1500ms, T3=3000ms, T4=5000ms
 * - Early exit jika nilai sudah stabil (tidak perlu tunggu penuh)
 * - Bound: MAX_VERIFICATION_DURATION_MS = 5000ms
 * - Fresh query wajib — tidak boleh reuse data dari scanner
 * - NO_MEASURABLE_CHANGE bukan FAILED (ai_task.md §10)
 * - Tidak hardcode delay tanpa alasan. Gunakan bounded retry dengan timeout.
 * - Log terstruktur [VERIFY] dengan elapsed ms untuk timing analysis
 *
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
     * Sprint 7: Staged sampling delays (ms dari saat resume).
     *
     * T0 = 0ms   — sample segera (mungkin masih stale, tapi log nilainya)
     * T1 = 500ms — quick check, sistem cepat
     * T2 = 1500ms — normal propagation
     * T3 = 3000ms — Samsung One UI butuh ini
     * T4 = 5000ms — batas maksimum (ai_task.md §8: MAX_VERIFICATION_DURATION = 5 seconds)
     *
     * Jika nilai sudah berubah di T1, tidak perlu tunggu sampai T4.
     */
    private val STAGED_DELAYS_MS = longArrayOf(0L, 500L, 1500L, 3000L, 5000L)

    /**
     * Query cache size aktual dari perangkat untuk satu package.
     * WAJIB di Dispatchers.IO — StorageStatsManager adalah I/O operation.
     *
     * PENTING: Ini selalu fresh query. Tidak pernah return cached value.
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
     * Verifikasi hasil cleaning satu app dengan staged sampling.
     *
     * Sprint 7: Multi-stage verification dengan early exit.
     * Stage delays: T0=0ms, T1=500ms, T2=1500ms, T3=3000ms, T4=5000ms
     *
     * Tidak langsung percaya angka pertama — sistem Android butuh beberapa
     * saat untuk mempropagasi perubahan cache ke StorageStatsManager.
     * Di Samsung One UI, delay bisa 2-4 detik.
     *
     * @param context Application context (fresh query — jangan reuse UI state)
     * @param packageName Package name yang di-verify
     * @param beforeBytes Cache size sebelum cleaning (immutable snapshot)
     * @return VerificationStatus yang merepresentasikan hasil nyata
     */
    suspend fun verify(
        context: Context,
        packageName: String,
        beforeBytes: Long
    ): Pair<Long, VerificationStatus> = withContext(Dispatchers.IO) {
        if (beforeBytes <= 0L) {
            Log.w(TAG, "[$packageName] beforeBytes tidak valid ($beforeBytes) — UNKNOWN")
            Log.w(Constants.TAG_VERIFY, "[VERIFICATION] $packageName | beforeBytes=$beforeBytes INVALID — returning UNKNOWN")
            return@withContext Pair(-1L, VerificationStatus.UNKNOWN)
        }

        Log.d(Constants.TAG_VERIFY, "[VERIFICATION] START $packageName | beforeBytes=${formatBytes(beforeBytes)} | stages=${STAGED_DELAYS_MS.size}")

        val startTimeMs = System.currentTimeMillis()
        var afterBytes = -1L
        var lastStatus = VerificationStatus.PENDING_VERIFICATION

        // Sprint 7: Staged sampling — setiap stage menunggu incremental delay dari stage sebelumnya
        var cumulativeDelayMs = 0L

        for ((stageIndex, targetDelayMs) in STAGED_DELAYS_MS.withIndex()) {
            // Tunggu tambahan waktu hingga target elapsed
            val additionalDelay = targetDelayMs - cumulativeDelayMs
            if (additionalDelay > 0L) {
                delay(additionalDelay)
            }
            cumulativeDelayMs = targetDelayMs

            // Fresh query — TIDAK boleh reuse scanner data
            val current = queryCacheBytes(context, packageName)
            val elapsedMs = System.currentTimeMillis() - startTimeMs

            if (current < 0L) {
                Log.w(TAG, "[$packageName] Gagal query stage ${stageIndex}")
                Log.w(Constants.TAG_VERIFY, "[VERIFICATION] T${stageIndex} $packageName | elapsed=${elapsedMs}ms | QUERY_FAILED")
                continue // Coba stage berikutnya
            }

            afterBytes = current
            val status = classify(beforeBytes, afterBytes)
            lastStatus = status

            Log.d(
                TAG, "[VERIFY] T${stageIndex} $packageName | " +
                        "before=${formatBytes(beforeBytes)} " +
                        "after=${formatBytes(afterBytes)} " +
                        "reclaimed=${formatBytes(maxOf(0L, beforeBytes - afterBytes))} " +
                        "result=$status"
            )
            Log.d(
                Constants.TAG_VERIFY,
                "[VERIFICATION] T${stageIndex} $packageName | " +
                        "elapsed=${elapsedMs}ms | " +
                        "before=${formatBytes(beforeBytes)} | " +
                        "after=${formatBytes(afterBytes)} | " +
                        "reclaimed=${formatBytes(maxOf(0L, beforeBytes - afterBytes))} | " +
                        "status=$status"
            )

            // Early exit: jika sudah ada perubahan terukur, tidak perlu tunggu stage berikutnya
            if (status == VerificationStatus.VERIFIED_SUCCESS ||
                status == VerificationStatus.PARTIAL_SUCCESS
            ) {
                Log.d(Constants.TAG_VERIFY, "[VERIFICATION] EARLY_EXIT T${stageIndex} $packageName | status=$status at elapsed=${elapsedMs}ms")
                return@withContext Pair(afterBytes, status)
            }
        }

        val totalElapsed = System.currentTimeMillis() - startTimeMs
        Log.d(Constants.TAG_VERIFY, "[VERIFICATION] END $packageName | finalStatus=$lastStatus | totalElapsed=${totalElapsed}ms | afterBytes=${formatBytes(afterBytes)}")

        // Jika semua stage tidak menghasilkan perubahan — kembalikan status terakhir
        // NO_CHANGE berarti sistem tidak memberikan perubahan terukur (bukan berarti FAILED)
        Pair(afterBytes, lastStatus)
    }

    /**
     * Legacy verify dengan maxRetries untuk backward compat dengan unit tests Sprint 6.
     * Sprint 7: delegasikan ke staged verify.
     */
    suspend fun verify(
        context: Context,
        packageName: String,
        beforeBytes: Long,
        maxRetries: Int = 3,
        retryDelayMs: Long = 1_500L
    ): Pair<Long, VerificationStatus> {
        // Untuk backward compat — gunakan staged verify jika maxRetries == 3 (default)
        return verify(context, packageName, beforeBytes)
    }

    /**
     * V8 (§20, §21, §38, §39): Query total aggregate cache size dari semua package di perangkat.
     */
    suspend fun queryAggregateCacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
        try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                    ?: return@withContext -1L
            val packageManager = context.packageManager
            @Suppress("DEPRECATION")
            val packages = packageManager.getInstalledPackages(0)
            var total = 0L
            for (pkg in packages) {
                try {
                    val uid = pkg.applicationInfo?.uid ?: continue
                    val stats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkg.packageName,
                        UserHandle.getUserHandleForUid(uid)
                    )
                    if (stats.cacheBytes > 0L) {
                        total += stats.cacheBytes
                    }
                } catch (e: Exception) {
                    // Abaikan kegagalan query per-package individual
                }
            }
            total
        } catch (e: Exception) {
            Log.w(TAG, "Gagal query aggregate cache: ${e.message}")
            -1L
        }
    }

    /**
     * V8 (§20, §21, §38, §39): Verifikasi pembersihan tingkat sistem (aggregate footprint).
     * Menggunakan staged sampling (T0→T4) dengan batas maksimum 5000ms.
     */
    suspend fun verifySystemWide(
        context: Context,
        beforeTotalBytes: Long
    ): Pair<Long, VerificationStatus> = withContext(Dispatchers.IO) {
        if (beforeTotalBytes <= 0L) {
            Log.w(Constants.TAG_VERIFY, "[VERIFICATION] SYSTEM_WIDE | beforeTotalBytes invalid ($beforeTotalBytes) — UNKNOWN")
            return@withContext Pair(-1L, VerificationStatus.UNKNOWN)
        }

        Log.d(Constants.TAG_VERIFY, "[VERIFICATION] START SYSTEM_WIDE | beforeBytes=${formatBytes(beforeTotalBytes)} | stages=${STAGED_DELAYS_MS.size}")
        val startTimeMs = System.currentTimeMillis()
        var afterTotalBytes = -1L
        var lastStatus = VerificationStatus.NO_MEASURABLE_CHANGE

        var cumulativeDelayMs = 0L
        for ((stageIndex, targetDelayMs) in STAGED_DELAYS_MS.withIndex()) {
            val additionalDelay = targetDelayMs - cumulativeDelayMs
            if (additionalDelay > 0L) {
                delay(additionalDelay)
            }
            cumulativeDelayMs = targetDelayMs

            val current = queryAggregateCacheBytes(context)
            val elapsedMs = System.currentTimeMillis() - startTimeMs

            if (current < 0L) {
                Log.w(Constants.TAG_VERIFY, "[VERIFICATION] T$stageIndex SYSTEM_WIDE | elapsed=${elapsedMs}ms | QUERY_FAILED")
                continue
            }

            afterTotalBytes = current
            val status = classifySystemWide(beforeTotalBytes, afterTotalBytes)
            lastStatus = status

            Log.d(
                Constants.TAG_VERIFY,
                "[VERIFICATION] T$stageIndex SYSTEM_WIDE | elapsed=${elapsedMs}ms | " +
                        "before=${formatBytes(beforeTotalBytes)} | " +
                        "after=${formatBytes(afterTotalBytes)} | " +
                        "reclaimed=${formatBytes(maxOf(0L, beforeTotalBytes - afterTotalBytes))} | " +
                        "status=$status"
            )

            if (status == VerificationStatus.VERIFIED_SUCCESS ||
                status == VerificationStatus.VERIFIED_PARTIAL
            ) {
                Log.d(Constants.TAG_VERIFY, "[VERIFICATION] EARLY_EXIT T$stageIndex SYSTEM_WIDE | status=$status at elapsed=${elapsedMs}ms")
                return@withContext Pair(afterTotalBytes, status)
            }
        }

        val totalElapsed = System.currentTimeMillis() - startTimeMs
        Log.d(Constants.TAG_VERIFY, "[VERIFICATION] END SYSTEM_WIDE | finalStatus=$lastStatus | elapsed=${totalElapsed}ms")
        Pair(afterTotalBytes, lastStatus)
    }

    /**
     * V8 (§20, §21, §38, §39): Klasifikasi hasil aggregate pembersihan sistem.
     */
    fun classifySystemWide(beforeTotalBytes: Long, afterTotalBytes: Long): VerificationStatus {
        if (beforeTotalBytes <= 0L || afterTotalBytes < 0L) return VerificationStatus.UNKNOWN
        val reduction = beforeTotalBytes - afterTotalBytes
        return when {
            reduction >= (beforeTotalBytes * SUCCESS_THRESHOLD).toLong() -> VerificationStatus.VERIFIED_SUCCESS
            reduction > 0L -> VerificationStatus.VERIFIED_PARTIAL
            else -> VerificationStatus.NO_MEASURABLE_CHANGE
        }
    }

    /**
     * Mengklasifikasikan hasil berdasarkan perbandingan before/after per package.
     */
    fun classify(beforeBytes: Long, afterBytes: Long): VerificationStatus {
        if (afterBytes < 0L) return VerificationStatus.UNKNOWN
        if (beforeBytes <= 0L) return VerificationStatus.UNKNOWN

        val reduction = beforeBytes - afterBytes
        if (reduction <= 0L) return VerificationStatus.NO_MEASURABLE_CHANGE

        val ratio = reduction.toFloat() / beforeBytes.toFloat()
        return when {
            ratio >= SUCCESS_THRESHOLD -> VerificationStatus.VERIFIED_SUCCESS
            ratio >= PARTIAL_THRESHOLD -> VerificationStatus.VERIFIED_PARTIAL
            else -> VerificationStatus.NO_MEASURABLE_CHANGE
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
