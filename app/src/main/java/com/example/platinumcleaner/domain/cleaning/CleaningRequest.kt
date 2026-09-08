package com.example.platinumcleaner.domain.cleaning

/**
 * CleaningRequest — Input ke CleaningOrchestrator.
 *
 * Immutable data class yang merepresentasikan satu sesi cleaning.
 * Orchestrator bertanggung jawab menafsirkan request ini dan
 * memilih strategy yang sesuai dengan capability perangkat.
 *
 * Sesuai ai_task.md §34: Semantics yang spesifik — requestCacheCleanup, bukan clearApp.
 */
data class CleaningRequest(
    /**
     * Daftar package name yang cache-nya akan dibersihkan.
     * Terurut berdasarkan prioritas (cache terbesar duluan).
     */
    val targetPackages: List<String>,

    /**
     * Ukuran cache sebelum cleaning, per package.
     * Digunakan oleh VerificationEngine untuk before/after comparison.
     * Key: packageName, Value: cacheBytes
     */
    val preCleanCacheBytes: Map<String, Long> = emptyMap(),

    /**
     * Capability yang diinginkan. Jika null, Orchestrator akan
     * memilih secara otomatis berdasarkan CapabilityResolver.
     */
    val preferredCapability: CleaningCapability? = null,

    /**
     * ID unik untuk request ini — digunakan untuk tracing di log.
     * Sesuai ai_task.md §27: structured local debug logging.
     */
    val requestId: String = java.util.UUID.randomUUID().toString().take(8)
)
