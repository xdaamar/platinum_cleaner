package com.example.platinumcleaner.service

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.platinumcleaner.Constants

/**
 * SettingsNodeResolver — Testable node matcher & safety validator (ai_task.md V9 §14, §15, §16, §50, §60).
 *
 * Mengimplementasikan:
 * - Strict Invariant Guard: MUTLAK menolak Clear Data / Hapus Data (§15)
 * - Multi-bahasa semantic detection untuk Clear Cache & Storage (§14)
 * - Confidence-based hierarchy (§50)
 * - Abstraksi murni yang dapat diuji dengan unit test tanpa device fisik (§60).
 */
object SettingsNodeResolver {

    private const val TAG = "SettingsNodeResolver"

    /**
     * Kata kunci BERBAHAYA yang menandakan penghapusan data pengguna.
     * Sesuai §15: DILARANG KERAS DIKLIK DALAM KONDISI APAPUN.
     */
    val CLEAR_DATA_FORBIDDEN_KEYWORDS = listOf(
        "clear data",
        "hapus data",
        "delete data",
        "clear storage",
        "hapus penyimpanan",
        "atur penyimpanan",
        "manage storage",
        "manage space",
        "kelola ruang",
        "kelola penyimpanan",
        "borrar datos",
        "daten löschen",
        "eliminar datos",
        "svuota memoria",
        "limpar dados"
    )

    /**
     * Kata kunci aman untuk tombol Clear Cache (multi-bahasa).
     * Sesuai §14: EN, ID, ES, DE, IT, PT.
     */
    val CLEAR_CACHE_SAFE_KEYWORDS = listOf(
        "clear cache",
        "hapus cache",
        "hapus memori",
        "bersihkan cache",
        "bersihkan memori",
        "cache löschen",
        "borrar caché",
        "borrar cache",
        "svuota cache",
        "limpar cache",
        "vider le cache"
    )

    /**
     * Kata kunci menu Storage/Penyimpanan di halaman App Info.
     */
    val STORAGE_MENU_KEYWORDS = listOf(
        "storage & cache",
        "penyimpanan & cache",
        "storage and cache",
        "storage",
        "penyimpanan",
        "penggunaan penyimpanan",
        "almacenamiento",
        "speicher",
        "archiviazione",
        "stockage"
    )

    /**
     * Known resource ID patterns untuk Clear Cache di berbagai OEM Android (AOSP, Samsung One UI, Xiaomi).
     */
    val CLEAR_CACHE_RESOURCE_IDS = listOf(
        "com.android.settings:id/button2",
        "com.android.settings:id/clear_cache_button",
        "com.android.settings:id/button_clear_cache",
        "android:id/button2"
    )

    /**
     * Hasil evaluasi node.
     */
    sealed class EvaluationResult {
        data class ClearCacheCandidate(val confidence: Float, val matchType: String) : EvaluationResult()
        data class StorageMenuCandidate(val confidence: Float) : EvaluationResult()
        object ClearDataBlocked : EvaluationResult()
        object NotTarget : EvaluationResult()
    }

    /**
     * Evaluasi node teks & resource ID untuk memastikan keamanan mutlak sebelum diklik (§15, §50).
     */
    fun evaluateNode(
        text: String?,
        contentDescription: String?,
        viewIdResourceName: String?
    ): EvaluationResult {
        val t = text?.trim()?.lowercase() ?: ""
        val desc = contentDescription?.trim()?.lowercase() ?: ""
        val combined = "$t $desc"
        val id = viewIdResourceName?.trim()?.lowercase() ?: ""

        // 1. STRICT SAFETY GUARD (§15): Tolak jika mengandung indikasi Clear Data
        if (isDangerousClearData(combined, id)) {
            Log.w(Constants.TAG_CLEAN, "[SAFETY_GUARD] REJECTED node with Clear Data indicators: text='$t', desc='$desc', id='$id'")
            return EvaluationResult.ClearDataBlocked
        }

        // 2. Resource ID exact match untuk Clear Cache (Confidence 1.0f)
        if (CLEAR_CACHE_RESOURCE_IDS.any { id.contains(it, ignoreCase = true) || id.endsWith("clear_cache_button") }) {
            return EvaluationResult.ClearCacheCandidate(confidence = 1.0f, matchType = "RESOURCE_ID")
        }

        // 3. Visible text exact semantic match untuk Clear Cache (Confidence 0.95f)
        val hasExactCacheText = CLEAR_CACHE_SAFE_KEYWORDS.any { keyword ->
            t == keyword || desc == keyword
        }
        if (hasExactCacheText) {
            return EvaluationResult.ClearCacheCandidate(confidence = 0.95f, matchType = "EXACT_TEXT")
        }

        // 4. Partial semantic text match untuk Clear Cache (Confidence 0.85f)
        val hasPartialCacheText = CLEAR_CACHE_SAFE_KEYWORDS.any { keyword ->
            combined.contains(keyword)
        }
        if (hasPartialCacheText) {
            return EvaluationResult.ClearCacheCandidate(confidence = 0.85f, matchType = "PARTIAL_TEXT")
        }

        // 5. Evaluasi Menu Storage
        val hasStorageMenu = STORAGE_MENU_KEYWORDS.any { keyword ->
            t == keyword || desc == keyword || combined.contains(keyword)
        }
        if (hasStorageMenu) {
            val confidence = if (t in STORAGE_MENU_KEYWORDS) 0.95f else 0.85f
            return EvaluationResult.StorageMenuCandidate(confidence)
        }

        return EvaluationResult.NotTarget
    }

    /**
     * Memeriksa apakah teks atau ID mengandung indikator destruktif Clear Data (§15).
     */
    fun isDangerousClearData(combinedText: String, resourceId: String = ""): Boolean {
        // Cek teks terlarang
        val hasForbiddenText = CLEAR_DATA_FORBIDDEN_KEYWORDS.any { keyword ->
            combinedText.contains(keyword)
        }
        if (hasForbiddenText) return true

        // Cek ID khusus clear data (misal button1 di Settings storage yang biasanya Clear Data)
        if (resourceId.contains("clear_data") || resourceId.contains("clear_storage")) {
            return true
        }

        return false
    }

    /**
     * Memverifikasi apakah window Settings saat ini menampilkan aplikasi target (§11).
     *
     * @param expectedLabel Nama tampilan aplikasi (misal: "Telegram")
     * @param observedTexts Teks-teks yang terlihat pada window Settings
     * @return true jika label aplikasi terlihat di Settings
     */
    fun verifyTargetAppWindow(expectedLabel: String, observedTexts: List<String>): Boolean {
        if (expectedLabel.isBlank()) return true
        val target = expectedLabel.trim().lowercase()
        return observedTexts.any { it.trim().lowercase() == target || it.trim().lowercase().contains(target) }
    }

    /**
     * Mencari node Clear Cache yang aman di dalam hierarki AccessibilityNodeInfo tree (§14, §15, §60).
     */
    @Suppress("DEPRECATION")
    fun findSafeClearCacheNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseForClearCache(root)
    }

    /**
     * Mencari node Storage di dalam hierarki AccessibilityNodeInfo tree.
     */
    @Suppress("DEPRECATION")
    fun findStorageNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseForStorage(root)
    }

    @Suppress("DEPRECATION")
    private fun traverseForClearCache(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val eval = evaluateNode(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName
        )

        when (eval) {
            is EvaluationResult.ClearDataBlocked -> {
                // Mutlak jangan gunakan node ini!
                return null
            }
            is EvaluationResult.ClearCacheCandidate -> {
                if (node.isClickable || node.isEnabled) {
                    Log.d(Constants.TAG_CLEAN, "[CACHE_BUTTON] matchType=${eval.matchType} confidence=${eval.confidence}")
                    return node
                }
            }
            else -> { /* Lanjut telusuri children */ }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = traverseForClearCache(child)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun traverseForStorage(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val eval = evaluateNode(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName
        )

        if (eval is EvaluationResult.StorageMenuCandidate) {
            if (node.isClickable || node.isEnabled) {
                Log.d(Constants.TAG_CLEAN, "[STORAGE_MENU] found confidence=${eval.confidence}")
                return node
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = traverseForStorage(child)
            if (found != null) return found
            child.recycle()
        }

        return null
    }
}
