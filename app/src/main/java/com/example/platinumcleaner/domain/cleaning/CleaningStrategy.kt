package com.example.platinumcleaner.domain.cleaning

import android.content.Context

/**
 * CleaningStrategy — Abstraction layer untuk semua cleaning mechanism.
 *
 * Sesuai ai_task.md §8: Orchestrator TIDAK BOLEH mengetahui detail
 * implementation setiap strategy. Dependency Inversion Principle.
 *
 * Contributor dapat menambahkan strategy baru (OEM-specific, future API)
 * tanpa mengubah Orchestrator, Scanner, UI, atau domain models.
 * Sesuai ai_task.md §30: Open-source friendly design.
 *
 * INVARIANT KEAMANAN (ai_task.md §34):
 * Setiap implementasi DILARANG melakukan:
 * - clearData / clear storage (hanya cache!)
 * - pm clear / destructive operations
 * - file system deletion dari app lain
 */
interface CleaningStrategy {

    /**
     * Capability yang diimplementasikan oleh strategy ini.
     * Digunakan oleh Orchestrator untuk memilih strategy yang tepat.
     */
    val capability: CleaningCapability

    /**
     * Apakah strategy ini tersedia pada perangkat saat ini?
     *
     * Cek kondisi runtime seperti API level, izin, atau availability service.
     * Dipanggil oleh CapabilityResolver saat mendeteksi kemampuan perangkat.
     * JANGAN melakukan expensive operation di sini.
     *
     * @param context Application context
     * @return true jika strategy bisa dieksekusi pada perangkat ini
     */
    fun isSupported(context: Context): Boolean

    /**
     * Eksekusi request pembersihan cache.
     *
     * Suspend function — HARUS berjalan di background thread (Dispatchers.IO atau Default).
     * JANGAN memblokir Main Thread.
     *
     * @param context Application context
     * @param request Detail cleaning yang diminta
     * @return CleaningResult dengan VerificationStatus yang jujur
     */
    suspend fun execute(context: Context, request: CleaningRequest): CleaningResult
}
