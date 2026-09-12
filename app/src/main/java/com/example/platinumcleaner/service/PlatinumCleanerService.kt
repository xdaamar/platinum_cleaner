package com.example.platinumcleaner.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.platinumcleaner.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PlatinumCleanerService — Auto-Clean Engine V2 (Sprint 5).
 *
 * ========================================================
 * SECURITY CRITICAL — 03_security_protocols.md
 * ========================================================
 * BARIS PERTAMA onAccessibilityEvent(): packageName guard. Titik.
 *
 * Sprint 5 Changes vs V1:
 * - Ganti Handler.postDelayed → coroutine + delay(4000) (non-blocking, presisi)
 * - Ganti findAccessibilityNodeInfosByText → AccessibilityNodeHelper.findNodeByPartialText()
 * - Hard timeout 15 detik per app via withTimeoutOrNull(15_000)
 * - Batch mode: setelah Success, ambil app berikutnya dari CleanSessionManager
 * - Emit ProgressUpdate untuk real-time overlay
 *
 * Sesuai 04_performance_budget.md: delay() via coroutine, BUKAN Thread.sleep()
 */
class PlatinumCleanerService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Flag untuk mencegah multiple coroutine dijalankan untuk event yang sama
    @Volatile
    private var isProcessingEvent = false

    // ===================================================
    // Lifecycle
    // ===================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100L
        }
        Log.d(Constants.TAG_SERVICE, "V2 Service tersambung")
    }

    override fun onInterrupt() {
        Log.w(Constants.TAG_SERVICE, "Service diinterupsi — membersihkan state")
        isProcessingEvent = false
        abortCurrentSession("Interrupted by system")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isProcessingEvent = false
        if (CleanSessionManager.isActive) CleanSessionManager.endSession()
        Log.d(Constants.TAG_SERVICE, "V2 Service dihentikan — semua resource dibersihkan")
    }

    // ===================================================
    // Core: Event Handler
    // ===================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // =========================================
        // HUKUM BESI — BARIS PERTAMA
        // =========================================
        if (event.packageName != Constants.SETTINGS_PACKAGE) {
            // Deteksi user tekan Back — kembali ke app kita
            if (CleanSessionManager.isActive &&
                event.packageName == Constants.OUR_PACKAGE_NAME
            ) {
                Log.w(Constants.TAG_SERVICE, "User kembali ke app — sesi dibatalkan")
                abortCurrentSession("User cancelled")
            }
            return
        }

        if (!CleanSessionManager.isActive) return

        if (CleanSessionManager.isStopRequested) {
            Log.d(Constants.TAG_SERVICE, "Stop requested, aborting session")
            abortCurrentSession("Stop requested")
            return
        }

        // Hanya proses TYPE_WINDOW_STATE_CHANGED untuk trigger fase baru
        // Ini menghindari spam dari TYPE_WINDOW_CONTENT_CHANGED yang terlalu sering
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Guard: jangan jalankan coroutine baru jika masih ada yang berjalan
        if (isProcessingEvent) {
            Log.d(Constants.TAG_SERVICE, "Event diabaikan — masih processing")
            return
        }

        Log.d(Constants.TAG_SERVICE, "Window state changed di Settings — memulai navigasi V2")
        startNavigationCoroutine()
    }

    // ===================================================
    // V2: Coroutine-based Navigation dengan Pacing Aman
    // ===================================================

    private fun startNavigationCoroutine() {
        isProcessingEvent = true

        serviceScope.launch {
            val targetPackage = CleanSessionManager.targetPackageName ?: run {
                isProcessingEvent = false
                return@launch
            }

            Log.d(Constants.TAG_SERVICE, "Memulai navigasi untuk: $targetPackage")

            // Hard timeout 15 detik per app (ai_task.md §3.2)
            val result = withTimeoutOrNull(Constants.HARD_TIMEOUT_MS) {
                navigateAndClearCache(targetPackage)
            }

            if (result == null) {
                // withTimeoutOrNull mengembalikan null → timeout tercapai
                Log.w(Constants.TAG_SERVICE, "TIMEOUT 15s untuk $targetPackage")
                emitAndProcessNext(targetPackage, success = false, reason = "Hard timeout 15s")
            }

            isProcessingEvent = false
        }
    }

    /**
     * Alur navigasi utama V2 dengan pacing dan verifikasi keamanan SettingsNodeResolver.
     * Sesuai ai_task.md §13, §14, §15, §16.
     *
     * @return true jika berhasil, false jika gagal di salah satu fase
     */
    private suspend fun navigateAndClearCache(targetPackage: String): Boolean {
        if (CleanSessionManager.isStopRequested) {
            Log.d(Constants.TAG_SERVICE, "Stop requested, aborting navigation for $targetPackage")
            return false
        }

        // === FASE 1: Memeriksa apakah tombol Clear Cache sudah terlihat langsung atau cari menu Storage ===
        Log.d(Constants.TAG_SERVICE, "Fase 1: Memeriksa window Settings untuk $targetPackage...")
        var clearNode = findSafeClearCacheWithRetry(maxRetries = 2, retryDelayMs = 500L)

        if (clearNode == null) {
            Log.d(Constants.TAG_SERVICE, "Fase 1: Mencari menu Storage via SettingsNodeResolver...")
            val storageNode = findStorageWithRetry(maxRetries = 3, retryDelayMs = 800L)

            if (storageNode == null) {
                Log.w(Constants.TAG_SERVICE, "Fase 1 GAGAL: menu Storage tidak ditemukan")
                emitAndProcessNext(targetPackage, success = false, reason = "Storage menu not found")
                return false
            }

            Log.d(Constants.TAG_SERVICE, "Fase 1 OK: Storage ditemukan — klik")
            AccessibilityNodeHelper.safeClick(storageNode)
            @Suppress("DEPRECATION") storageNode.recycle()

            if (CleanSessionManager.isStopRequested) return false

            // ⏱️ PACING anti-bot Android
            Log.d(Constants.TAG_SERVICE, "Pacing setelah klik Storage...")
            delay(Constants.PACING_DELAY_MS)

            if (CleanSessionManager.isStopRequested) return false

            // === FASE 2: Cari tombol Clear Cache yang aman di dalam menu Storage ===
            Log.d(Constants.TAG_SERVICE, "Fase 2: Mencari tombol Clear Cache yang aman...")
            clearNode = findSafeClearCacheWithRetry(maxRetries = 4, retryDelayMs = 800L)
        }

        if (clearNode == null) {
            Log.w(Constants.TAG_SERVICE, "Fase 2 GAGAL: tombol hapus cache tidak ditemukan atau ditolak oleh safety guard")
            emitAndProcessNext(targetPackage, success = false, reason = "Clear cache button not found or unsafe")
            return false
        }

        if (!clearNode.isEnabled) {
            Log.d(Constants.TAG_SERVICE, "Cache sudah kosong (button disabled)")
            @Suppress("DEPRECATION") clearNode.recycle()
            emitAndProcessNext(targetPackage, success = true, reason = null)
            return true
        }

        Log.d(Constants.TAG_SERVICE, "Fase 2 OK: '${clearNode.text}' aman ditemukan — klik")
        AccessibilityNodeHelper.safeClick(clearNode)
        @Suppress("DEPRECATION") clearNode.recycle()

        if (CleanSessionManager.isStopRequested) return false

        // ⏱️ PACING setelah klik Clear Cache
        Log.d(Constants.TAG_SERVICE, "Pacing setelah klik Clear Cache...")
        delay(Constants.PACING_DELAY_MS)

        // === FASE 3: Konfirmasi dialog (jika ada) ===
        Log.d(Constants.TAG_SERVICE, "Fase 3: Memeriksa dialog konfirmasi...")
        val root = rootInActiveWindow
        if (root != null) {
            var confirmNode = AccessibilityNodeHelper.findNodeByPartialText(
                root, AccessibilityNodeHelper.CONFIRM_KEYWORDS
            )

            // Fallback heuristik jika teks tidak cocok
            if (confirmNode == null) {
                confirmNode = AccessibilityNodeHelper.findConfirmButtonFallback(root)
                if (confirmNode != null) {
                    Log.d(Constants.TAG_SERVICE, "Fase 3 (fallback heuristic): tombol konfirmasi ditemukan")
                }
            }

            if (confirmNode != null) {
                AccessibilityNodeHelper.safeClick(confirmNode)
                @Suppress("DEPRECATION") confirmNode.recycle()
                Log.d(Constants.TAG_SERVICE, "Fase 3 OK: konfirmasi diklik")
            } else {
                Log.d(Constants.TAG_SERVICE, "Fase 3: tidak ada dialog konfirmasi — lanjut")
            }
            @Suppress("DEPRECATION") root.recycle()
        }

        if (CleanSessionManager.isStopRequested) return false

        // ⏱️ PACING setelah konfirmasi
        delay(Constants.PACING_DELAY_MS)

        // === FASE 4: Selesai untuk target ini ===
        emitAndProcessNext(targetPackage, success = true, reason = null)
        return true
    }

    // ===================================================
    // Helper: Cari Safe Clear Cache & Storage dengan retry
    // ===================================================

    private suspend fun findSafeClearCacheWithRetry(
        maxRetries: Int = 3,
        retryDelayMs: Long = 800L
    ): android.view.accessibility.AccessibilityNodeInfo? {
        repeat(maxRetries) { attempt ->
            val root = rootInActiveWindow
            if (root != null) {
                val node = SettingsNodeResolver.findSafeClearCacheNode(root)
                @Suppress("DEPRECATION") root.recycle()
                if (node != null) return node
            }
            if (attempt < maxRetries - 1) {
                Log.d(Constants.TAG_SERVICE, "Safe Clear Cache node tidak ditemukan, retry ${attempt + 1}/$maxRetries...")
                delay(retryDelayMs)
            }
        }
        return null
    }

    private suspend fun findStorageWithRetry(
        maxRetries: Int = 3,
        retryDelayMs: Long = 800L
    ): android.view.accessibility.AccessibilityNodeInfo? {
        repeat(maxRetries) { attempt ->
            val root = rootInActiveWindow
            if (root != null) {
                val node = SettingsNodeResolver.findStorageNode(root)
                @Suppress("DEPRECATION") root.recycle()
                if (node != null) return node
            }
            if (attempt < maxRetries - 1) {
                Log.d(Constants.TAG_SERVICE, "Storage node tidak ditemukan, retry ${attempt + 1}/$maxRetries...")
                delay(retryDelayMs)
            }
        }
        return null
    }

    // ===================================================
    // Session Completion, Bounded Return, & Batch Queue
    // ===================================================

    /**
     * Menyelesaikan app saat ini dan memproses app berikutnya dalam antrian.
     */
    private fun emitAndProcessNext(
        packageName: String,
        success: Boolean,
        reason: String?
    ) {
        serviceScope.launch {
            if (success) {
                ServiceEventBus.emitEvent(CleanerEvent.Success(packageName))
                Log.d(Constants.TAG_SERVICE, "✅ Berhasil: $packageName")
            } else {
                ServiceEventBus.emitEvent(
                    CleanerEvent.Failed(packageName, reason ?: "Unknown error")
                )
                Log.e(Constants.TAG_SERVICE, "❌ Gagal: $packageName — $reason")
            }

            if (CleanSessionManager.isStopRequested) {
                Log.d(Constants.TAG_SERVICE, "Stop diminta, mengakhiri sesi dan kembali ke aplikasi")
                CleanSessionManager.endSession()
                ServiceEventBus.emitEvent(CleanerEvent.AllCompleted)
                returnToPlatinumCleaner()
                return@launch
            }

            // Cek antrian berikutnya
            val nextPackage = CleanSessionManager.moveToNext()
            if (nextPackage != null) {
                // Emit progress update untuk overlay
                ServiceEventBus.emitEvent(
                    CleanerEvent.ProgressUpdate(
                        currentIndex = CleanSessionManager.currentIndex,
                        totalApps = CleanSessionManager.totalApps,
                        currentAppName = nextPackage
                    )
                )

                // Buka halaman app berikutnya di Settings via AppInfoNavigator
                Log.d(Constants.TAG_SERVICE, "Pindah ke app berikutnya: $nextPackage")
                delay(1_000L) // jeda singkat antar-app

                val navResult = com.example.platinumcleaner.platform.cleaning.AppInfoNavigator.navigateToAppInfo(
                    applicationContext,
                    nextPackage
                )
                if (navResult != com.example.platinumcleaner.domain.cleaning.NavigationResult.APP_INFO_OPENED) {
                    Log.w(Constants.TAG_SERVICE, "Gagal membuka App Info untuk $nextPackage: $navResult")
                    emitAndProcessNext(nextPackage, success = false, reason = navResult.name)
                }

            } else {
                // Semua app selesai
                CleanSessionManager.endSession()
                ServiceEventBus.emitEvent(CleanerEvent.AllCompleted)
                Log.d(Constants.TAG_SERVICE, "🏁 Semua app dalam antrian selesai")
                returnToPlatinumCleaner()
            }
        }
    }

    /**
     * Bounded return ke aplikasi Platinum Cleaner (§16).
     * Membuka kembali aplikasi Platinum Cleaner atau melakukan bounded Back.
     */
    private fun returnToPlatinumCleaner() {
        Log.d(Constants.TAG_SERVICE, "Kembali ke Platinum Cleaner...")
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(Constants.OUR_PACKAGE_NAME)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Gagal kembali ke Platinum Cleaner via Intent: ${e.message}")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun abortCurrentSession(reason: String) {
        val target = CleanSessionManager.targetPackageName ?: "unknown"
        CleanSessionManager.endSession()
        serviceScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Failed(target, reason))
        }
    }
}
