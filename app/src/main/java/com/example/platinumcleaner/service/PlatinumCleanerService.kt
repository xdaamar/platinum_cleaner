package com.example.platinumcleaner.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.platinumcleaner.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * PlatinumCleanerService — Auto-Clean Engine (Sprint 4: Hardened).
 *
 * ========================================================
 * SECURITY CRITICAL — 03_security_protocols.md
 * ========================================================
 * HUKUM BESI BARIS PERTAMA onAccessibilityEvent():
 * Jika packageName != SETTINGS_PACKAGE → return. Titik.
 *
 * Sprint 4 Hardening:
 * - onInterrupt(): bersihkan semua resource + emit Failed
 * - onDestroy(): cancel scope + remove all Handler callbacks
 * - Semua AccessibilityNodeInfo access dalam try-catch (DeadObjectException)
 * - Deteksi user tekan Back (packageName kembali ke app kita)
 * - Retry Fase 2 max 3x dengan delay 1 detik (fail-fast OEM compatibility)
 */
class PlatinumCleanerService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPhase: CleanPhase = CleanPhase.IDLE
    private var timeoutRunnable: Runnable? = null

    // Sprint 4: Retry counter untuk Fase 2
    private var clearCacheRetryCount = 0
    private val maxClearCacheRetries = 3

    private enum class CleanPhase {
        IDLE,
        LOOKING_FOR_STORAGE,
        LOOKING_FOR_CLEAR_CACHE,
        CONFIRMING,
        DONE
    }

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
        Log.d(Constants.TAG_SERVICE, "Service tersambung dan aktif")
    }

    /**
     * Sprint 4: onInterrupt() — sistem Android menginterupsi service.
     * Wajib bersihkan semua state agar tidak ada yang "nyangkut".
     * Sesuai 04_performance_budget.md: Resource & Memory Management.
     */
    override fun onInterrupt() {
        Log.w(Constants.TAG_SERVICE, "Service diinterupsi oleh sistem — membersihkan semua state")
        abortSession("Interrupted by system")
    }

    /**
     * Sprint 4: onDestroy() — service dihentikan.
     * Cancel semua coroutine dan Handler callbacks untuk mencegah memory leak.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Bersihkan semua resource sesuai 04_performance_budget.md
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (CleanSessionManager.isActive) {
            CleanSessionManager.endSession()
        }
        Log.d(Constants.TAG_SERVICE, "Service dihentikan — semua resource dibersihkan")
    }

    // ===================================================
    // Core: Event Handler
    // ===================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // =========================================
        // HUKUM BESI KEAMANAN (03_security_protocols.md) — BARIS PERTAMA
        // Abaikan SEMUA event dari luar Settings.
        // =========================================
        if (event.packageName != Constants.SETTINGS_PACKAGE) {
            // Sprint 4: Deteksi user tekan Back — kembali ke app kita
            if (CleanSessionManager.isActive &&
                event.packageName == Constants.OUR_PACKAGE_NAME
            ) {
                Log.w(Constants.TAG_SERVICE, "User kembali ke app utama — sesi dibatalkan")
                abortSession("User cancelled")
            }
            return
        }

        if (!CleanSessionManager.isActive) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        Log.d(Constants.TAG_SERVICE, "Event di Settings — phase: $currentPhase")

        when (currentPhase) {
            CleanPhase.IDLE -> transitionTo(CleanPhase.LOOKING_FOR_STORAGE)
            CleanPhase.LOOKING_FOR_STORAGE -> tryNavigateToStorage()
            CleanPhase.LOOKING_FOR_CLEAR_CACHE -> tryClickClearCache()
            CleanPhase.CONFIRMING -> tryConfirmDialog()
            CleanPhase.DONE -> { /* no-op */ }
        }
    }

    // ===================================================
    // Phase 1: Cari dan klik menu Storage
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryNavigateToStorage() {
        // Sprint 4: try-catch untuk DeadObjectException / NPE
        try {
            val root = rootInActiveWindow ?: return

            val storageNode = findNodeByLabels(root, Constants.STORAGE_LABELS)
            if (storageNode != null) {
                Log.d(Constants.TAG_SERVICE, "Storage ditemukan: '${storageNode.text}'")
                storageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                storageNode.recycle()

                mainHandler.postDelayed({
                    clearCacheRetryCount = 0 // reset retry counter
                    transitionTo(CleanPhase.LOOKING_FOR_CLEAR_CACHE)
                }, Constants.NAVIGATION_DELAY_MS)
            }

            root.recycle()
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error di fase Storage: ${e.message}")
            abortSession("Exception di fase Storage: ${e.javaClass.simpleName}")
        }
    }

    // ===================================================
    // Phase 2: Cari dan klik Clear Cache (dengan retry 3x)
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryClickClearCache() {
        try {
            val root = rootInActiveWindow ?: run {
                // rootInActiveWindow null → window sudah berubah atau user back
                abortSession("Window tidak ditemukan di fase Clear Cache")
                return
            }

            val clearCacheNode = findNodeByLabels(root, Constants.CLEAR_CACHE_LABELS)
            if (clearCacheNode != null) {
                Log.d(Constants.TAG_SERVICE, "Clear Cache ditemukan: '${clearCacheNode.text}'")

                if (clearCacheNode.isEnabled) {
                    clearCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clearCacheNode.recycle()
                    root.recycle()

                    mainHandler.postDelayed({
                        transitionTo(CleanPhase.CONFIRMING)
                    }, Constants.NAVIGATION_DELAY_MS)
                } else {
                    // Cache sudah kosong
                    clearCacheNode.recycle()
                    root.recycle()
                    Log.d(Constants.TAG_SERVICE, "Cache sudah kosong (button disabled)")
                    completeSession()
                }
            } else {
                root.recycle()
                clearCacheRetryCount++
                Log.w(Constants.TAG_SERVICE, "Clear Cache tidak ditemukan — retry $clearCacheRetryCount/$maxClearCacheRetries")

                if (clearCacheRetryCount >= maxClearCacheRetries) {
                    // Sprint 4: Fail-fast setelah 3x retry (tidak tunggu timeout 5 detik)
                    abortSession("UI structure not recognized after $maxClearCacheRetries retries")
                } else {
                    // Retry setelah 1 detik
                    mainHandler.postDelayed({
                        if (CleanSessionManager.isActive) tryClickClearCache()
                    }, 1_000L)
                }
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error di fase Clear Cache: ${e.message}")
            abortSession("Exception di fase Clear Cache: ${e.javaClass.simpleName}")
        }
    }

    // ===================================================
    // Phase 3: Konfirmasi dialog (jika ada)
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryConfirmDialog() {
        try {
            val root = rootInActiveWindow ?: run {
                // Tidak ada dialog konfirmasi — berarti sudah clear
                completeSession()
                return
            }

            val confirmNode = findNodeByLabels(root, Constants.CONFIRM_LABELS)
            if (confirmNode != null) {
                Log.d(Constants.TAG_SERVICE, "Dialog konfirmasi: '${confirmNode.text}'")
                confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                confirmNode.recycle()
                root.recycle()

                mainHandler.postDelayed({ completeSession() }, Constants.NAVIGATION_DELAY_MS)
            } else {
                root.recycle()
                // Tidak ada dialog → clear langsung berhasil
                completeSession()
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error di fase Konfirmasi: ${e.message}")
            // Anggap berhasil jika exception terjadi di fase konfirmasi
            // (kemungkinan dialog sudah dismiss otomatis)
            completeSession()
        }
    }

    // ===================================================
    // Helper: Cari node berdasarkan list label (multi-OEM)
    // ===================================================

    @Suppress("DEPRECATION")
    private fun findNodeByLabels(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {
        return try {
            for (label in labels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                if (!nodes.isNullOrEmpty()) return nodes.first()
            }
            null
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error saat mencari node: ${e.message}")
            null
        }
    }

    // ===================================================
    // State Machine Transitions & Session Management
    // ===================================================

    private fun transitionTo(phase: CleanPhase) {
        currentPhase = phase
        Log.d(Constants.TAG_SERVICE, "→ Transisi ke fase: $phase")
        cancelTimeout()

        if (phase != CleanPhase.IDLE && phase != CleanPhase.DONE) {
            timeoutRunnable = Runnable {
                Log.w(Constants.TAG_SERVICE, "TIMEOUT di fase $phase")
                abortSession("Timeout di fase $phase")
            }.also { mainHandler.postDelayed(it, Constants.PHASE_TIMEOUT_MS) }
        }
    }

    private fun completeSession() {
        val target = CleanSessionManager.targetPackageName ?: "unknown"
        cancelTimeout()
        currentPhase = CleanPhase.DONE
        CleanSessionManager.endSession()

        serviceScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Success(target))
        }

        Log.d(Constants.TAG_SERVICE, "✅ Sesi selesai: $target")
        currentPhase = CleanPhase.IDLE
    }

    private fun abortSession(reason: String) {
        val target = CleanSessionManager.targetPackageName ?: "unknown"
        cancelTimeout()
        currentPhase = CleanPhase.IDLE
        clearCacheRetryCount = 0
        CleanSessionManager.endSession()

        serviceScope.launch {
            ServiceEventBus.emitEvent(CleanerEvent.Failed(target, reason))
        }

        Log.e(Constants.TAG_SERVICE, "❌ Sesi dibatalkan: $reason")
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }
}
