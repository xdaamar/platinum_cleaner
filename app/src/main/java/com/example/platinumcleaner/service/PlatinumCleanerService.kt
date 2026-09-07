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
 * PlatinumCleanerService — Auto-Clean Engine.
 *
 * ========================================================
 * SECURITY CRITICAL — 03_security_protocols.md
 * ========================================================
 *
 * HUKUM BESI BARIS PERTAMA:
 * Setiap event yang masuk WAJIB diperiksa packageName-nya.
 * Service ini TIDAK AKAN PERNAH berinteraksi dengan aplikasi
 * selain "com.android.settings". Titik.
 *
 * ARSITEKTUR STATE MACHINE:
 * Service menggunakan state machine sederhana untuk melacak fase navigasi:
 *
 *   IDLE → NAVIGATING → LOOKING_FOR_STORAGE → LOOKING_FOR_CLEAR_CACHE → CONFIRMING → DONE
 *
 * Setiap transisi punya timeout 5 detik. Jika timeout tercapai sebelum
 * node yang diharapkan ditemukan, sesi dibatalkan dan CleanerEvent.Failed di-emit.
 */
class PlatinumCleanerService : AccessibilityService() {

    // Scope untuk emit ke ServiceEventBus dari dalam service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    // State machine phase tracking
    private var currentPhase: CleanPhase = CleanPhase.IDLE
    private var timeoutRunnable: Runnable? = null

    // ===================================================
    // Enum: Phase State Machine
    // ===================================================
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cancelTimeout()
        Log.d(Constants.TAG_SERVICE, "Service dihentikan")
    }

    override fun onInterrupt() {
        Log.d(Constants.TAG_SERVICE, "Service diinterupsi — membatalkan sesi")
        abortSession("Service interrupted by system")
    }

    // ===================================================
    // Core: Event Handler
    // ===================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // =========================================
        // HUKUM BESI KEAMANAN (03_security_protocols.md)
        // Baris pertama: abaikan SEMUA event dari luar Settings.
        // Mencegah clickjacking, UI redressing, dan eksekusi liar.
        // =========================================
        if (event.packageName != Constants.SETTINGS_PACKAGE) {
            return
        }

        // Hanya proses jika ada sesi aktif yang dipicu user
        if (!CleanSessionManager.isActive) {
            return
        }

        // Hanya proses saat ada perubahan window/konten yang berarti
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        Log.d(Constants.TAG_SERVICE, "Event di Settings — phase: $currentPhase")

        when (currentPhase) {
            CleanPhase.IDLE -> {
                // Sesi baru dimulai — transisi ke fase pertama
                transitionTo(CleanPhase.LOOKING_FOR_STORAGE)
            }
            CleanPhase.LOOKING_FOR_STORAGE -> tryNavigateToStorage()
            CleanPhase.LOOKING_FOR_CLEAR_CACHE -> tryClickClearCache()
            CleanPhase.CONFIRMING -> tryConfirmDialog()
            CleanPhase.DONE -> { /* Tidak ada aksi */ }
        }
    }

    // ===================================================
    // Phase 1: Cari dan klik menu Storage
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryNavigateToStorage() {
        val root = rootInActiveWindow ?: return

        val storageNode = findNodeByLabels(root, Constants.STORAGE_LABELS)
        if (storageNode != null) {
            Log.d(Constants.TAG_SERVICE, "Storage ditemukan: '${storageNode.text}'")
            storageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            storageNode.recycle()

            mainHandler.postDelayed({
                transitionTo(CleanPhase.LOOKING_FOR_CLEAR_CACHE)
            }, Constants.NAVIGATION_DELAY_MS)
        }

        root.recycle()
    }

    // ===================================================
    // Phase 2: Cari dan klik tombol Clear Cache
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryClickClearCache() {
        val root = rootInActiveWindow ?: return

        val clearCacheNode = findNodeByLabels(root, Constants.CLEAR_CACHE_LABELS)
        if (clearCacheNode != null) {
            Log.d(Constants.TAG_SERVICE, "Clear Cache ditemukan: '${clearCacheNode.text}'")

            // Periksa apakah tombol bisa diklik (cache mungkin sudah kosong)
            if (clearCacheNode.isEnabled) {
                clearCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clearCacheNode.recycle()

                mainHandler.postDelayed({
                    // Beberapa OEM menampilkan dialog konfirmasi, beberapa langsung clear
                    // Coba cek dialog konfirmasi dulu, jika tidak ada anggap sudah selesai
                    transitionTo(CleanPhase.CONFIRMING)
                }, Constants.NAVIGATION_DELAY_MS)
            } else {
                // Cache sudah kosong — tidak perlu konfirmasi
                Log.d(Constants.TAG_SERVICE, "Cache sudah kosong (button disabled)")
                clearCacheNode.recycle()
                completeSession()
            }
        }

        root.recycle()
    }

    // ===================================================
    // Phase 3: Konfirmasi dialog (jika ada)
    // ===================================================

    @Suppress("DEPRECATION")
    private fun tryConfirmDialog() {
        val root = rootInActiveWindow ?: return

        val confirmNode = findNodeByLabels(root, Constants.CONFIRM_LABELS)
        if (confirmNode != null) {
            Log.d(Constants.TAG_SERVICE, "Dialog konfirmasi ditemukan: '${confirmNode.text}'")
            confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            confirmNode.recycle()

            mainHandler.postDelayed({
                completeSession()
            }, Constants.NAVIGATION_DELAY_MS)
        } else {
            // Tidak ada dialog konfirmasi — langsung selesai
            Log.d(Constants.TAG_SERVICE, "Tidak ada dialog konfirmasi — sesi selesai")
            completeSession()
        }

        root.recycle()
    }

    // ===================================================
    // Helper: Cari node berdasarkan list label (multi-OEM)
    // ===================================================

    private fun findNodeByLabels(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (nodes != null && nodes.isNotEmpty()) {
                return nodes.first()
            }
        }
        return null
    }

    // ===================================================
    // State Machine Transitions & Session Management
    // ===================================================

    private fun transitionTo(phase: CleanPhase) {
        currentPhase = phase
        Log.d(Constants.TAG_SERVICE, "→ Transisi ke fase: $phase")

        // Reset timeout setiap kali masuk fase baru
        cancelTimeout()

        if (phase != CleanPhase.IDLE && phase != CleanPhase.DONE) {
            // Set timeout: jika dalam 5 detik tidak ada progres, batalkan sesi
            timeoutRunnable = Runnable {
                Log.w(Constants.TAG_SERVICE, "Timeout di fase $phase — membatalkan sesi")
                abortSession("Timeout di fase $phase: node tidak ditemukan dalam ${Constants.PHASE_TIMEOUT_MS}ms")
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

        Log.d(Constants.TAG_SERVICE, "✅ Sesi selesai dengan sukses untuk: $target")
        currentPhase = CleanPhase.IDLE
    }

    private fun abortSession(reason: String) {
        val target = CleanSessionManager.targetPackageName ?: "unknown"
        cancelTimeout()
        currentPhase = CleanPhase.IDLE
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
