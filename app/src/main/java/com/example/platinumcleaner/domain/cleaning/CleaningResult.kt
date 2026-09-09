package com.example.platinumcleaner.domain.cleaning

/**
 * VerificationStatus — Hasil verifikasi aktual setelah cleaning.
 *
 * Sprint 7: Expanded states sesuai ai_task.md §6.
 * Setiap state memiliki makna spesifik dan UI message yang berbeda.
 * Tidak ada collapse ke generic "FAILED".
 *
 * UI harus menampilkan status yang jujur — tidak memanipulasi user.
 */
enum class VerificationStatus {
    // ===== Execution States =====

    /** Intent tidak tersedia di perangkat ini (resolveActivity == null). */
    INTENT_UNAVAILABLE,

    /** startActivity() gagal (ActivityNotFoundException / SecurityException / crash). */
    INTENT_LAUNCH_FAILED,

    /** Intent berhasil diluncurkan, menunggu sistem Android memproses. */
    WAITING_FOR_SYSTEM_ACTION,

    // ===== Verification States =====

    /** Cleaning sudah dieksekusi tapi belum bisa diverifikasi (menunggu resume). */
    PENDING_VERIFICATION,

    /** Verifikasi berjalan (staged sampling sedang berlangsung). */
    VERIFYING,

    /** Verifikasi timeout — sistem tidak memberikan hasil dalam waktu yang ditetapkan. */
    VERIFICATION_TIMEOUT,

    // ===== Result States =====

    /** Cache terbukti berkurang signifikan (>= 90% threshold). */
    VERIFIED_SUCCESS,

    /** Cache berkurang tapi tidak sebanyak yang diperkirakan (10-89%). */
    VERIFIED_PARTIAL,

    /** Cache tidak berubah sama sekali — BUKAN berarti gagal, mungkin sudah bersih. */
    NO_MEASURABLE_CHANGE,

    /** User menekan Back / membatalkan system UI — tidak ada pembersihan yang terjadi. */
    USER_CANCELLED,

    // ===== Legacy / Compat States =====

    /** Cache tidak berubah sama sekali (alias NO_MEASURABLE_CHANGE untuk backward compat). */
    NO_CHANGE,

    /** Proses gagal sebelum cleaning bisa dieksekusi (execution error). */
    FAILED,

    /** Tidak ada cleaning capability yang tersedia di perangkat ini. */
    UNSUPPORTED,

    /** Tidak bisa membandingkan — data before/after tidak tersedia. */
    UNKNOWN,

    /** Cache berkurang sebagian (alias VERIFIED_PARTIAL untuk backward compat). */
    PARTIAL_SUCCESS,
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
