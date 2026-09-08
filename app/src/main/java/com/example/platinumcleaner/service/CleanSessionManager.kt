package com.example.platinumcleaner.service

import android.util.Log
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.VerificationStatus

/**
 * CleanSessionManager — Strategy-agnostic session state machine.
 *
 * Sprint 6 Refactor: Diubah dari "queue manager untuk AccessibilityService"
 * menjadi pure state machine yang tidak tahu tentang Accessibility sama sekali.
 *
 * States:
 * Idle → Scanning → Planning → ResolvingCapability →
 * Executing → WaitingForResume → Verifying → Completed
 *
 * Failure path:
 * Executing → Failed → FallbackAvailable → (fallback atau user decision)
 *
 * Sesuai ai_task.md §17: Strategy-agnostic state machine.
 * Sesuai ai_task.md §18: Session survive lifecycle events (in-memory untuk sprint ini).
 */
object CleanSessionManager {

    private const val TAG = "CleanSessionManager"

    // ===================================================
    // State Machine
    // ===================================================

    enum class SessionState {
        IDLE,
        SCANNING,
        PLANNING,
        RESOLVING_CAPABILITY,
        EXECUTING,
        WAITING_FOR_RESUME,
        VERIFYING,
        COMPLETED,
        FAILED
    }

    @Volatile
    var currentState: SessionState = SessionState.IDLE
        private set

    // ===================================================
    // Session Data
    // ===================================================

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var targetPackageName: String? = null
        private set

    @Volatile
    var selectedCapability: CleaningCapability? = null
        private set

    @Volatile
    var pendingResult: CleaningResult? = null
        private set

    // Antrian untuk batch mode
    private val targetQueue: ArrayDeque<String> = ArrayDeque()

    @Volatile
    var currentIndex: Int = 0
        private set

    @Volatile
    var totalApps: Int = 0
        private set

    // ===================================================
    // State Transitions
    // ===================================================

    /**
     * Mulai sesi untuk satu app.
     * @return false jika sesi sebelumnya masih aktif (double-execution guard).
     */
    fun startSession(packageName: String): Boolean {
        if (isActive) {
            Log.w(TAG, "Sesi masih aktif untuk $targetPackageName — request diabaikan")
            return false
        }
        targetQueue.clear()
        targetQueue.add(packageName)
        totalApps = 1
        currentIndex = 0
        targetPackageName = packageName
        isActive = true
        transitionTo(SessionState.PLANNING)
        Log.d(TAG, "Sesi dimulai: $packageName")
        return true
    }

    /**
     * Mulai sesi batch untuk beberapa app.
     */
    fun startBatchSession(packages: List<String>): Boolean {
        if (isActive) {
            Log.w(TAG, "Sesi masih aktif — request batch diabaikan")
            return false
        }
        if (packages.isEmpty()) return false
        targetQueue.clear()
        targetQueue.addAll(packages)
        totalApps = packages.size
        currentIndex = 0
        targetPackageName = targetQueue.first()
        isActive = true
        transitionTo(SessionState.PLANNING)
        Log.d(TAG, "Sesi batch dimulai: ${packages.size} app")
        return true
    }

    fun markExecuting(capability: CleaningCapability) {
        selectedCapability = capability
        transitionTo(SessionState.EXECUTING)
    }

    fun markWaitingForResume(result: CleaningResult) {
        pendingResult = result
        transitionTo(SessionState.WAITING_FOR_RESUME)
    }

    fun markVerifying() {
        transitionTo(SessionState.VERIFYING)
    }

    fun markCompleted() {
        transitionTo(SessionState.COMPLETED)
        endSession()
    }

    fun markFailed() {
        transitionTo(SessionState.FAILED)
        endSession()
    }

    /**
     * Pindah ke app berikutnya (batch mode).
     * @return Package name berikutnya, atau null jika antrian habis.
     */
    fun moveToNext(): String? {
        if (targetQueue.isNotEmpty()) targetQueue.removeFirst()
        currentIndex++
        return if (targetQueue.isNotEmpty()) {
            targetPackageName = targetQueue.first()
            Log.d(TAG, "Batch: app ${currentIndex + 1}/$totalApps → $targetPackageName")
            transitionTo(SessionState.EXECUTING)
            targetPackageName
        } else {
            Log.d(TAG, "Antrian habis — semua app selesai")
            null
        }
    }

    /**
     * Akhiri sesi — bersihkan semua state.
     */
    fun endSession() {
        Log.d(TAG, "Sesi diakhiri. State: $currentState | Target terakhir: $targetPackageName")
        targetQueue.clear()
        targetPackageName = null
        selectedCapability = null
        pendingResult = null
        currentIndex = 0
        totalApps = 0
        isActive = false
        currentState = SessionState.IDLE
    }

    private fun transitionTo(state: SessionState) {
        Log.d(TAG, "State: $currentState → $state")
        currentState = state
    }
}
