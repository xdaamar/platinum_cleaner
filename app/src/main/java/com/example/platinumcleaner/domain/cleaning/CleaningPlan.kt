package com.example.platinumcleaner.domain.cleaning

/**
 * CleaningMode — Mode pembersihan aktual sesuai Sprint V8 PRD Override (§2, §19, §30).
 *
 * Memisahkan secara tegas:
 * - SYSTEM_WIDE: Operasi sistem global (StorageManager.ACTION_CLEAR_APP_CACHE).
 *   Satu operasi sistem, tidak boleh pura-pura antri per aplikasi.
 * - PER_APP_ASSISTED: Membuka App Info (ACTION_APPLICATION_DETAILS_SETTINGS)
 *   untuk diarahkan ke tombol Clear Cache manual.
 * - PER_APP_AUTOMATED: Otomasi klik via AccessibilityService (hanya bila diizinkan).
 */
enum class CleaningMode(val displayTitle: String) {
    SYSTEM_WIDE("Pembersihan Sistem Android"),
    PER_APP_ASSISTED("Pembersihan Terarah Manual"),
    PER_APP_AUTOMATED("Pembersihan Otomatis Terbantu")
}

/**
 * CleaningPlan — Representasi rencana kerja cleaning engine (§31).
 *
 * Sesuai ai_task.md §30-§32:
 * Orchestrator menjalankan CleaningPlan yang jelas semantiknya.
 */
data class CleaningPlan(
    val mode: CleaningMode,
    val targets: List<String> = emptyList(),
    val totalTargets: Int = targets.size,
    val estimatedReclaim: Long = 0L,
    val requiresUserInteraction: Boolean = (mode != CleaningMode.PER_APP_AUTOMATED),
    val automationEnabled: Boolean = (mode == CleaningMode.PER_APP_AUTOMATED)
)
