package com.example.platinumcleaner.domain.cleaning

import java.util.UUID

/**
 * CleaningSessionState — Status state machine untuk sesi pembersihan (ai_task.md §7 & §18).
 */
enum class CleaningSessionState {
    IDLE,
    PREPARING,
    OPENING_APP_INFO,
    WAITING_FOR_APP_INFO,
    AUTOMATING,
    WAITING_FOR_USER,
    VERIFYING,
    SUCCESS,
    NO_CHANGE,
    SKIPPED,
    FAILED,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED
}

/**
 * CleaningSession — Model sesi pembersihan berurutan (ai_task.md §7, §8, §18, §19).
 *
 * Persyaratan:
 * - sessionId unik mencegah double execution
 * - targets berisi daftar target pembersihan riil tanpa batasan 5 aplikasi
 * - currentIndex menunjukkan index target yang sedang aktif
 * - state melacak status terkini
 * - stop & skip didukung secara aman dan deterministik
 */
data class CleaningSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val targets: List<CleaningTarget> = emptyList(),
    val currentIndex: Int = 0,
    val mode: CleaningMode = CleaningMode.PER_APP_ASSISTED,
    val state: CleaningSessionState = CleaningSessionState.IDLE,
    val targetResults: Map<String, VerificationStatus> = emptyMap(),
    val reclaimedBytesMap: Map<String, Long> = emptyMap()
) {
    val totalTargets: Int
        get() = targets.size

    val currentTarget: CleaningTarget?
        get() = targets.getOrNull(currentIndex)

    val isRunning: Boolean
        get() = state != CleaningSessionState.IDLE &&
                state != CleaningSessionState.COMPLETED &&
                state != CleaningSessionState.STOPPED

    val totalReclaimedBytes: Long
        get() = reclaimedBytesMap.values.sum()

    val successCount: Int
        get() = targetResults.values.count {
            it == VerificationStatus.VERIFIED_SUCCESS || it == VerificationStatus.VERIFIED_PARTIAL
        }

    val skippedCount: Int
        get() = targetResults.values.count { it == VerificationStatus.USER_SKIPPED }

    val failedCount: Int
        get() = targetResults.values.count {
            it == VerificationStatus.FAILED ||
                    it == VerificationStatus.AUTOMATION_FAILED ||
                    it == VerificationStatus.NAVIGATION_FAILED
        }

    val noChangeCount: Int
        get() = targetResults.values.count { it == VerificationStatus.NO_MEASURABLE_CHANGE }

    /**
     * Memajukan ke target berikutnya secara aman dan idempoten (§18, §19).
     */
    fun nextTarget(): CleaningSession {
        val nextIndex = currentIndex + 1
        return if (nextIndex < targets.size) {
            copy(
                currentIndex = nextIndex,
                state = CleaningSessionState.PREPARING
            )
        } else {
            copy(
                state = CleaningSessionState.COMPLETED
            )
        }
    }

    /**
     * Menandai target saat ini sebagai di-skip oleh pengguna (§26).
     */
    fun skipCurrentTarget(): CleaningSession {
        val currentPkg = currentTarget?.packageName ?: return this
        val updatedResults = targetResults + (currentPkg to VerificationStatus.USER_SKIPPED)
        val nextIndex = currentIndex + 1
        return if (nextIndex < targets.size) {
            copy(
                currentIndex = nextIndex,
                state = CleaningSessionState.PREPARING,
                targetResults = updatedResults
            )
        } else {
            copy(
                state = CleaningSessionState.COMPLETED,
                targetResults = updatedResults
            )
        }
    }

    /**
     * Meminta penghentian sesi (§8, §30).
     */
    fun requestStop(): CleaningSession {
        return copy(state = CleaningSessionState.STOP_REQUESTED)
    }

    /**
     * Menyelesaikan penghentian setelah operasi aman selesai (§8).
     */
    fun markStopped(): CleaningSession {
        return copy(state = CleaningSessionState.STOPPED)
    }

    /**
     * Mencatat hasil verifikasi target saat ini (§22, §25, §70).
     */
    fun recordResult(packageName: String, status: VerificationStatus, reclaimedBytes: Long): CleaningSession {
        val updatedResults = targetResults + (packageName to status)
        val updatedReclaimed = if (status == VerificationStatus.VERIFIED_SUCCESS || status == VerificationStatus.VERIFIED_PARTIAL) {
            reclaimedBytesMap + (packageName to maxOf(0L, reclaimedBytes))
        } else {
            reclaimedBytesMap + (packageName to 0L)
        }
        val targetState = when (status) {
            VerificationStatus.VERIFIED_SUCCESS, VerificationStatus.VERIFIED_PARTIAL -> CleaningSessionState.SUCCESS
            VerificationStatus.USER_SKIPPED -> CleaningSessionState.SKIPPED
            VerificationStatus.NO_MEASURABLE_CHANGE -> CleaningSessionState.NO_CHANGE
            VerificationStatus.STOPPED -> CleaningSessionState.STOPPED
            else -> CleaningSessionState.FAILED
        }
        return copy(
            state = targetState,
            targetResults = updatedResults,
            reclaimedBytesMap = updatedReclaimed
        )
    }
}
