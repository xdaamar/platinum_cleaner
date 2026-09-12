package com.example.platinumcleaner.service

import android.util.Log
import com.example.platinumcleaner.domain.cleaning.CleaningCapability
import com.example.platinumcleaner.domain.cleaning.CleaningMode
import com.example.platinumcleaner.domain.cleaning.CleaningResult
import com.example.platinumcleaner.domain.cleaning.CleaningSession
import com.example.platinumcleaner.domain.cleaning.CleaningSessionState
import com.example.platinumcleaner.domain.cleaning.CleaningTarget
import com.example.platinumcleaner.domain.cleaning.VerificationStatus

/**
 * CleanSessionManager — Strategy-agnostic session state machine (ai_task.md V9 §7, §8, §18, §19).
 *
 * Bertanggung jawab mengelola lifecycle sesi pembersihan aktif:
 * - Menyimpan CleaningSession immutable yang diperbarui secara transisional
 * - Mencegah double execution via UUID session ID & guard isActive
 * - Mendukung STOP global (§8, §30) dan SKIP per-aplikasi (§26)
 * - Idempoten terhadap callback lifecycle Android.
 */
object CleanSessionManager {

    private const val TAG = "CleanSessionManager"

    enum class SessionState {
        IDLE,
        SCANNING,
        PLANNING,
        RESOLVING_CAPABILITY,
        EXECUTING,
        WAITING_FOR_RESUME,
        VERIFYING,
        STOP_REQUESTED,
        STOPPED,
        COMPLETED,
        FAILED
    }

    @Volatile
    var currentState: SessionState = SessionState.IDLE
        private set

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var isStopRequested: Boolean = false
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

    @Volatile
    var activeSession: CleaningSession? = null
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
        isStopRequested = false

        val target = CleaningTarget(packageName, packageName, 0L, isEligible = true)
        activeSession = CleaningSession(
            targets = listOf(target),
            currentIndex = 0,
            mode = CleaningMode.PER_APP_ASSISTED,
            state = CleaningSessionState.PREPARING
        )

        transitionTo(SessionState.PLANNING)
        Log.d(TAG, "Sesi dimulai: $packageName")
        return true
    }

    /**
     * Mulai sesi batch untuk beberapa app dengan CleaningTarget riil (§5, §7).
     */
    fun startBatchSessionWithTargets(targets: List<CleaningTarget>, mode: CleaningMode): Boolean {
        if (isActive) {
            Log.w(TAG, "Sesi masih aktif — request batch diabaikan")
            return false
        }
        if (targets.isEmpty()) return false
        targetQueue.clear()
        targetQueue.addAll(targets.map { it.packageName })
        totalApps = targets.size
        currentIndex = 0
        targetPackageName = targetQueue.first()
        isActive = true
        isStopRequested = false

        activeSession = CleaningSession(
            targets = targets,
            currentIndex = 0,
            mode = mode,
            state = CleaningSessionState.PREPARING
        )

        transitionTo(SessionState.PLANNING)
        Log.d(TAG, "Sesi batch dimulai: ${targets.size} targets | mode=$mode")
        return true
    }

    /**
     * Backward-compat overload: Mulai sesi batch dengan list package string.
     */
    fun startBatchSession(packages: List<String>): Boolean {
        val targets = packages.map { CleaningTarget(it, it, 0L, isEligible = true) }
        return startBatchSessionWithTargets(targets, CleaningMode.PER_APP_ASSISTED)
    }

    fun markExecuting(capability: CleaningCapability) {
        selectedCapability = capability
        activeSession = activeSession?.copy(state = CleaningSessionState.AUTOMATING)
        transitionTo(SessionState.EXECUTING)
    }

    fun markWaitingForResume(result: CleaningResult) {
        pendingResult = result
        activeSession = activeSession?.copy(state = CleaningSessionState.WAITING_FOR_USER)
        transitionTo(SessionState.WAITING_FOR_RESUME)
    }

    fun markVerifying() {
        activeSession = activeSession?.copy(state = CleaningSessionState.VERIFYING)
        transitionTo(SessionState.VERIFYING)
    }

    fun markCompleted() {
        activeSession = activeSession?.copy(state = CleaningSessionState.COMPLETED)
        transitionTo(SessionState.COMPLETED)
        endSession()
    }

    fun markFailed() {
        activeSession = activeSession?.copy(state = CleaningSessionState.FAILED)
        transitionTo(SessionState.FAILED)
        endSession()
    }

    /**
     * Meminta penghentian sesi secara aman (§8, §30).
     * Operasi yang sedang berlangsung diselesaikan sebelum beralih ke STOPPED.
     */
    fun requestStop() {
        Log.d(TAG, "[STOP] Permintaan stop diterima. Menyelesaikan operasi aman terkini...")
        isStopRequested = true
        activeSession = activeSession?.requestStop()
        transitionTo(SessionState.STOP_REQUESTED)
    }

    /**
     * Melewati aplikasi yang sedang aktif dan melanjutkan ke aplikasi berikutnya (§26).
     */
    fun skipCurrent(): String? {
        Log.d(TAG, "[SKIP] User meminta skip untuk target: $targetPackageName")
        activeSession = activeSession?.skipCurrentTarget()
        return moveToNext()
    }

    /**
     * Pindah ke app berikutnya (batch mode).
     * @return Package name berikutnya, atau null jika antrian habis atau stop diminta.
     */
    fun moveToNext(): String? {
        if (isStopRequested) {
            Log.d(TAG, "[STOP] Stop diminta — membatalkan navigasi target berikutnya.")
            activeSession = activeSession?.markStopped()
            transitionTo(SessionState.STOPPED)
            endSession()
            return null
        }

        if (targetQueue.isNotEmpty()) targetQueue.removeFirst()
        currentIndex++
        activeSession = activeSession?.nextTarget()

        return if (targetQueue.isNotEmpty()) {
            targetPackageName = targetQueue.first()
            Log.d(TAG, "Batch: app ${currentIndex + 1}/$totalApps → $targetPackageName")
            transitionTo(SessionState.EXECUTING)
            targetPackageName
        } else {
            Log.d(TAG, "Antrian habis — semua app selesai")
            activeSession = activeSession?.copy(state = CleaningSessionState.COMPLETED)
            transitionTo(SessionState.COMPLETED)
            null
        }
    }

    /**
     * Batalkan sesi pembersihan aktif.
     */
    fun cancelSession() {
        Log.d(TAG, "Sesi dibatalkan oleh pengguna. Target saat ini: $targetPackageName")
        activeSession = activeSession?.markStopped()
        endSession()
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
        isStopRequested = false
        currentState = SessionState.IDLE
    }

    private fun transitionTo(state: SessionState) {
        Log.d(TAG, "State: $currentState → $state")
        currentState = state
    }
}
