package com.example.platinumcleaner.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ServiceEventBus — Jembatan komunikasi reaktif antara AccessibilityService dan ViewModel.
 *
 * Sprint 5: Tambah event baru:
 * - ProgressUpdate: update real-time untuk overlay progress
 * - AllCompleted: semua app dalam antrian sudah selesai dibersihkan
 *
 * replay = 0: Tidak ada event lama yang di-replay — anti ghost event & anti memory leak.
 */
object ServiceEventBus {
    private val _events = MutableSharedFlow<CleanerEvent>(replay = 0)
    val events = _events.asSharedFlow()

    suspend fun emitEvent(event: CleanerEvent) {
        _events.emit(event)
    }
}

/**
 * Sealed class event dari Auto-Clean Engine V2.
 */
sealed class CleanerEvent {
    /** Service mulai navigasi ke Settings untuk app target. */
    data class Started(val packageName: String) : CleanerEvent()

    /**
     * Sprint 5: Update progress real-time untuk overlay.
     * Di-emit setiap kali service berpindah ke app berikutnya.
     */
    data class ProgressUpdate(
        val currentIndex: Int,
        val totalApps: Int,
        val currentAppName: String
    ) : CleanerEvent()

    /** Clear Cache berhasil untuk satu app. */
    data class Success(val packageName: String) : CleanerEvent()

    /** Proses gagal — timeout, node tidak ditemukan, atau user cancel. */
    data class Failed(val packageName: String, val reason: String) : CleanerEvent()

    /** Sprint 5: Semua app dalam antrian sudah selesai diproses. */
    object AllCompleted : CleanerEvent()
}
