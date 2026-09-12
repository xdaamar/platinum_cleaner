package com.example.platinumcleaner.domain.cleaning

import java.util.UUID

/**
 * InteractiveQueue — Model antrean pembersihan interaktif sekuensial (Opsi 1 / Sprint V10).
 *
 * Mengelola urutan aplikasi yang dibersihkan secara interaktif:
 * - Menampung daftar CleaningTarget
 * - Melacak target aktif saat ini (`currentTarget`) dan target berikutnya (`nextTarget`)
 * - Operasi immutable: `advance()`, `skip()`, dan `stop()`
 * - Menghitung progres persentase secara deterministik.
 */
data class InteractiveQueue(
    val sessionId: String = UUID.randomUUID().toString(),
    val targets: List<CleaningTarget> = emptyList(),
    val currentIndex: Int = 0,
    val isCompleted: Boolean = false,
    val isStopped: Boolean = false,
    val processedPackages: Set<String> = emptySet(),
    val skippedPackages: Set<String> = emptySet()
) {
    val totalTargets: Int
        get() = targets.size

    val currentTarget: CleaningTarget?
        get() = if (currentIndex in targets.indices && !isCompleted && !isStopped) targets[currentIndex] else null

    val nextTarget: CleaningTarget?
        get() = if (currentIndex + 1 in targets.indices && !isCompleted && !isStopped) targets[currentIndex + 1] else null

    val hasNext: Boolean
        get() = currentIndex + 1 < targets.size && !isCompleted && !isStopped

    val progressFraction: Float
        get() = if (targets.isEmpty()) 0f else (currentIndex.toFloat() / targets.size.toFloat()).coerceIn(0f, 1f)

    val remainingTargetsCount: Int
        get() = (targets.size - currentIndex).coerceAtLeast(0)

    val totalCacheBytes: Long
        get() = targets.sumOf { it.cacheBytesBefore }

    /**
     * Memajukan antrean ke target berikutnya setelah aplikasi saat ini selesai dibersihkan.
     */
    fun advance(): InteractiveQueue {
        if (isCompleted || isStopped || targets.isEmpty()) return this
        val currentPkg = currentTarget?.packageName
        val newProcessed = if (currentPkg != null) processedPackages + currentPkg else processedPackages
        val nextIndex = currentIndex + 1
        return copy(
            currentIndex = nextIndex,
            processedPackages = newProcessed,
            isCompleted = nextIndex >= targets.size
        )
    }

    /**
     * Melewati aplikasi saat ini dan langsung berpindah ke target berikutnya.
     */
    fun skip(): InteractiveQueue {
        if (isCompleted || isStopped || targets.isEmpty()) return this
        val currentPkg = currentTarget?.packageName
        val newSkipped = if (currentPkg != null) skippedPackages + currentPkg else skippedPackages
        val nextIndex = currentIndex + 1
        return copy(
            currentIndex = nextIndex,
            skippedPackages = newSkipped,
            isCompleted = nextIndex >= targets.size
        )
    }

    /**
     * Menghentikan antrean pembersihan secara aman.
     */
    fun stop(): InteractiveQueue {
        return copy(isStopped = true)
    }
}
