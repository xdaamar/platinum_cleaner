package com.example.platinumcleaner.domain.cleaning

/**
 * CleaningTarget — Model target pembersihan spesifik (ai_task.md §5).
 *
 * Persyaratan:
 * - packageName unik
 * - cacheBytesBefore nilai riil dari scan
 * - appLabel nama tampilan aplikasi dari PackageManager
 * - isEligible menandakan apakah aplikasi valid untuk pembersihan otomatis
 * - Aplikasi dengan cache 0 B ditandai isEligible = false (tidak masuk target auto-clean).
 */
data class CleaningTarget(
    val packageName: String,
    val appLabel: String,
    val cacheBytesBefore: Long,
    val isEligible: Boolean = cacheBytesBefore > 0L
)
