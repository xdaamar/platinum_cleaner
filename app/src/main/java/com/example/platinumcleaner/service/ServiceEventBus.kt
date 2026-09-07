package com.example.platinumcleaner.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ServiceEventBus — Jembatan komunikasi reaktif antara AccessibilityService dan ViewModel.
 *
 * ARSITEKTUR:
 * AccessibilityService berjalan di lifecycle terpisah dari ViewModel/Composable.
 * Kita tidak bisa inject ViewModel ke Service secara langsung (memory leak).
 *
 * SOLUSI: Singleton SharedFlow dengan replay = 0.
 *   - replay = 0: Tidak ada event lama yang di-emit ulang ke subscriber baru.
 *     Ini mencegah ViewModel yang baru di-create menerima event dari sesi cleaning
 *     sebelumnya — anti memory leak dan anti ghost event.
 *   - SharedFlow (bukan StateFlow): Kita ingin event satu kali (one-shot), bukan
 *     state persisten. "Clear cache berhasil" harus hanya diproses sekali.
 *
 * Sesuai 04_performance_budget.md: Tidak ada blocking call. Semua reaktif via Flow.
 */
object ServiceEventBus {
    private val _events = MutableSharedFlow<CleanerEvent>(replay = 0)
    val events = _events.asSharedFlow()

    suspend fun emitEvent(event: CleanerEvent) {
        _events.emit(event)
    }
}

/**
 * Sealed class yang merepresentasikan semua kemungkinan event dari Auto-Clean Engine.
 * Sesuai 02_coding_standards.md: Type Safety, tidak ada raw String untuk status.
 */
sealed class CleanerEvent {
    /** Service mulai mengarahkan user ke halaman app di Settings. */
    data class Started(val packageName: String) : CleanerEvent()

    /** Clear Cache berhasil dilakukan untuk app target. */
    data class Success(val packageName: String) : CleanerEvent()

    /**
     * Proses gagal — node tidak ditemukan dalam timeout, atau service diinterupsi.
     * @param reason Pesan deskriptif untuk debugging. Tidak ditampilkan langsung ke user.
     */
    data class Failed(val packageName: String, val reason: String) : CleanerEvent()
}
