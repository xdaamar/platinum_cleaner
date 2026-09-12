package com.example.platinumcleaner.platform.cleaning

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.CleaningTarget
import com.example.platinumcleaner.domain.cleaning.InteractiveQueue
import com.example.platinumcleaner.domain.cleaning.NavigationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InteractiveCleanManager — Koordinator sesi pembersihan interaktif berantai (Opsi 1 / Sprint V10).
 *
 * Bertanggung jawab:
 * 1. Mengelola lifecycle antrean interaktif (`InteractiveQueue`).
 * 2. Mengurutkan aplikasi dari cache terbesar ke terkecil.
 * 3. Membuka halaman Settings aplikasi target berikutnya secara instan via `AppInfoNavigator`.
 * 4. Mendukung navigasi instan: `advanceToNext()`, `skipCurrent()`, dan `stopSession()`.
 * 5. Bounded return ke aplikasi Platinum Cleaner setelah antrean selesai.
 */
object InteractiveCleanManager {

    private const val TAG = "InteractiveCleanMgr"

    private val _activeQueue = MutableStateFlow<InteractiveQueue?>(null)
    val activeQueue: StateFlow<InteractiveQueue?> = _activeQueue.asStateFlow()

    val isSessionActive: Boolean
        get() = _activeQueue.value?.let { !it.isCompleted && !it.isStopped } ?: false

    val currentTarget: CleaningTarget?
        get() = _activeQueue.value?.currentTarget

    val nextTarget: CleaningTarget?
        get() = _activeQueue.value?.nextTarget

    /**
     * Memulai sesi pembersihan interaktif dengan daftar target.
     *
     * @param context Application context
     * @param targets Daftar target pembersihan
     * @return true jika sesi berhasil dimulai
     */
    fun startSession(context: Context, targets: List<CleaningTarget>): Boolean {
        if (targets.isEmpty()) {
            Log.w(TAG, "Daftar target kosong, sesi tidak dapat dimulai")
            return false
        }

        // Urutkan dari cache terbesar ke terkecil
        val sortedTargets = targets
            .filter { it.isEligible && it.cacheBytesBefore > 0L }
            .sortedByDescending { it.cacheBytesBefore }

        if (sortedTargets.isEmpty()) {
            Log.w(TAG, "Tidak ada target yang memenuhi syarat (> 0 B)")
            return false
        }

        val initialQueue = InteractiveQueue(targets = sortedTargets)
        _activeQueue.value = initialQueue

        Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Sesi dimulai dengan ${sortedTargets.size} target. Target 1: ${sortedTargets.first().appLabel}")

        // Buka aplikasi pertama
        val firstTarget = sortedTargets.first()
        val navResult = AppInfoNavigator.navigateToAppInfo(context, firstTarget.packageName)
        if (navResult != NavigationResult.APP_INFO_OPENED) {
            Log.w(TAG, "Gagal membuka target pertama: $navResult")
        }

        return true
    }

    /**
     * Melangkah ke aplikasi berikutnya dalam antrean.
     *
     * @param context Application context
     * @return Target baru, atau null jika antrean selesai
     */
    fun advanceToNext(context: Context): CleaningTarget? {
        val currentQueue = _activeQueue.value ?: return null
        val updatedQueue = currentQueue.advance()
        _activeQueue.value = updatedQueue

        val next = updatedQueue.currentTarget
        if (next != null) {
            Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Beralih ke: ${next.appLabel} (${updatedQueue.currentIndex + 1}/${updatedQueue.totalTargets})")
            val navResult = AppInfoNavigator.navigateToAppInfo(context, next.packageName)
            if (navResult != NavigationResult.APP_INFO_OPENED) {
                Log.w(TAG, "Gagal membuka target ${next.packageName}: $navResult")
            }
            return next
        } else {
            Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Seluruh antrean selesai! Kembali ke aplikasi...")
            finishSession(context)
            return null
        }
    }

    /**
     * Melewati aplikasi saat ini dan membuka target berikutnya.
     *
     * @param context Application context
     * @return Target baru, atau null jika antrean selesai
     */
    fun skipCurrent(context: Context): CleaningTarget? {
        val currentQueue = _activeQueue.value ?: return null
        val updatedQueue = currentQueue.skip()
        _activeQueue.value = updatedQueue

        val next = updatedQueue.currentTarget
        if (next != null) {
            Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Melewati aplikasi. Target baru: ${next.appLabel}")
            val navResult = AppInfoNavigator.navigateToAppInfo(context, next.packageName)
            if (navResult != NavigationResult.APP_INFO_OPENED) {
                Log.w(TAG, "Gagal membuka target ${next.packageName}: $navResult")
            }
            return next
        } else {
            Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Antrean selesai setelah skip. Kembali ke aplikasi...")
            finishSession(context)
            return null
        }
    }

    /**
     * Menghentikan sesi pembersihan interaktif atas permintaan pengguna.
     */
    fun stopSession(context: Context) {
        val currentQueue = _activeQueue.value
        _activeQueue.value = currentQueue?.stop()
        Log.d(Constants.TAG_CLEAN, "[INTERACTIVE] Sesi dihentikan oleh pengguna")
        finishSession(context)
    }

    /**
     * Menyelesaikan sesi dan membawa pengguna kembali ke Platinum Cleaner.
     */
    private fun finishSession(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(Constants.OUR_PACKAGE_NAME)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal meluncurkan kembali ke Platinum Cleaner: ${e.message}")
        }
    }

    /**
     * Mereset state antrean.
     */
    fun clearSession() {
        _activeQueue.value = null
    }
}
