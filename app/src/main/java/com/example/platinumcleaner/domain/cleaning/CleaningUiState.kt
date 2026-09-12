package com.example.platinumcleaner.domain.cleaning

/**
 * CleaningUiState — Model status UI terpadu untuk sesi pembersihan (ai_task.md §71).
 *
 * Single Source of Truth bagi dashboard dan overlay pembersihan:
 * - isRunning: true jika sesi sedang aktif berjalan
 * - currentIndex: index target saat ini (1-based untuk tampilan user)
 * - totalTargets: total target dalam antrian
 * - currentPackage: package name aplikasi yang sedang diproses
 * - currentAppLabel: nama tampilan aplikasi
 * - currentBeforeBytes: cache awal aplikasi
 * - currentAfterBytes: cache sesudah dibersihkan (-1 jika belum diverifikasi)
 * - currentStatus: status verifikasi terkini
 * - totalReclaimedBytes: total byte yang berhasil dibersihkan dari aplikasi yang sukses
 * - processedCount: jumlah aplikasi yang telah diproses
 * - succeededCount: jumlah aplikasi yang berhasil dibersihkan
 * - partialCount: jumlah aplikasi yang berhasil sebagian
 * - skippedCount: jumlah aplikasi yang dilewati (skip)
 * - failedCount: jumlah aplikasi yang gagal
 * - isStopped: true jika sesi dihentikan oleh pengguna
 * - activeMode: mode pembersihan aktif
 */
data class CleaningUiState(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val totalTargets: Int = 0,
    val currentPackage: String? = null,
    val currentAppLabel: String? = null,
    val currentBeforeBytes: Long = 0L,
    val currentAfterBytes: Long = -1L,
    val currentStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    val sessionState: CleaningSessionState = CleaningSessionState.IDLE,
    val totalReclaimedBytes: Long = 0L,
    val processedCount: Int = 0,
    val succeededCount: Int = 0,
    val partialCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val isStopped: Boolean = false,
    val activeMode: CleaningMode = CleaningMode.PER_APP_ASSISTED
)
