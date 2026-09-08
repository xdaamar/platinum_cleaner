package com.example.platinumcleaner.domain.cleaning

/**
 * VerificationStatus — Hasil verifikasi aktual setelah cleaning.
 *
 * Sesuai ai_task.md §14: Jangan hanya Boolean success.
 * UI harus menampilkan status yang jujur.
 */
enum class VerificationStatus {
    /** Cache terbukti berkurang signifikan (> threshold minimum). */
    VERIFIED_SUCCESS,

    /** Cache berkurang tapi tidak sebanyak yang diperkirakan. */
    PARTIAL_SUCCESS,

    /** Cache tidak berubah sama sekali — mungkin sudah bersih sebelumnya. */
    NO_CHANGE,

    /** Proses gagal sebelum cleaning bisa dieksekusi. */
    FAILED,

    /** Cleaning sudah dieksekusi tapi belum bisa diverifikasi (menunggu resume). */
    PENDING_VERIFICATION,

    /** Tidak bisa membandingkan — data before/after tidak tersedia. */
    UNKNOWN
}

/**
 * AppCleanResult — Hasil cleaning untuk satu aplikasi spesifik.
 */
data class AppCleanResult(
    val packageName: String,
    val appName: String,
    val beforeBytes: Long,
    val afterBytes: Long,
    val status: VerificationStatus,
    val usedCapability: CleaningCapability,
    val errorMessage: String? = null
) {
    /** Jumlah bytes yang berhasil dibebaskan (>= 0). */
    val reclaimedBytes: Long get() = maxOf(0L, beforeBytes - afterBytes)

    /** True jika ada pengurangan cache yang terukur. */
    val isCacheReduced: Boolean get() = reclaimedBytes > 0L
}

/**
 * CleaningResult — Hasil keseluruhan satu sesi cleaning.
 *
 * Sesuai ai_task.md §3 (New Product Requirement):
 * SUCCESS = "cleaning capability yang tersedia sudah dieksekusi dan
 * hasil aktual sudah diverifikasi semaksimal mungkin."
 */
data class CleaningResult(
    val requestId: String,
    val appResults: List<AppCleanResult>,
    val selectedCapability: CleaningCapability,
    val overallStatus: VerificationStatus
) {
    /** Total bytes yang terverifikasi berhasil dibebaskan. */
    val totalReclaimedBytes: Long
        get() = appResults.sumOf { it.reclaimedBytes }

    /** Jumlah app yang berhasil dibersihkan. */
    val successCount: Int
        get() = appResults.count {
            it.status == VerificationStatus.VERIFIED_SUCCESS ||
                    it.status == VerificationStatus.PARTIAL_SUCCESS
        }

    /** Jumlah app yang tidak berubah. */
    val noChangeCount: Int
        get() = appResults.count { it.status == VerificationStatus.NO_CHANGE }

    /** Jumlah app yang gagal. */
    val failedCount: Int
        get() = appResults.count { it.status == VerificationStatus.FAILED }
}
