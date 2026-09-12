package com.example.platinumcleaner.service

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.platinumcleaner.Constants

/**
 * AccessibilityNodeHelper — Multi-Language Heuristic Node Matcher.
 *
 * Sprint 5: Menggantikan `findAccessibilityNodeInfosByText()` yang bersifat exact-match
 * dan sering gagal pada perangkat berbahasa Indonesia / OEM yang memodifikasi teks UI.
 *
 * STRATEGI:
 * 1. Primary: Traversal rekursif ke SEMUA child node, cek `text.contains(keyword, ignoreCase=true)`
 * 2. Secondary: Cek `contentDescription` (untuk node yang ikon-only)
 * 3. Fallback Heuristik: Cari via viewIdResourceName atau className
 *
 * Sesuai 02_coding_standards.md: Single Responsibility, utility murni tanpa side effects.
 */
object AccessibilityNodeHelper {

    // ===================================================
    // Kamus Multi-Bahasa (EN / ID / ES)
    // Sesuai ai_task.md §3.1
    // ===================================================

    val STORAGE_KEYWORDS = listOf(
        "storage",
        "penyimpanan",
        "storage & cache",
        "penyimpanan & cache",
        "storage and cache",
        "almacenamiento",
        "penggunaan penyimpanan",
        "memory"
    )

    val CLEAR_CACHE_KEYWORDS = listOf(
        "clear cache",
        "hapus cache",
        "bersihkan memori",
        "bersihkan cache",
        "borrar caché",
        "borrar cache",
        "hapus data cache",
        "delete cache"
    )

    val CONFIRM_KEYWORDS = listOf(
        "ok",
        "ya",
        "yes",
        "aceptar",
        "determinar",
        "confirm",
        "oke"
    )

    // Resource ID dialog positif standar Android
    private const val ANDROID_BUTTON1_ID = "android:id/button1"
    private const val ANDROID_BUTTON_CLASSNAME = "android.widget.Button"

    // ===================================================
    // Primary: Recursive Partial-Text Traversal
    // ===================================================

    /**
     * Mencari node dengan melakukan traversal rekursif ke seluruh hierarki accessibility tree.
     *
     * Tidak seperti `findAccessibilityNodeInfosByText()` yang hanya melakukan exact/prefix match,
     * fungsi ini mengecek `text.contains(keyword, ignoreCase=true)` sehingga bisa mencocokkan
     * teks parsial dalam berbagai bahasa.
     *
     * @param root Node akar untuk mulai traversal (biasanya `rootInActiveWindow`)
     * @param keywords Daftar kata kunci dari kamus multi-bahasa
     * @param requireClickable Jika true, hanya kembalikan node yang bisa diklik
     * @return Node pertama yang cocok, atau null jika tidak ada
     */
    @Suppress("DEPRECATION")
    fun findNodeByPartialText(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        requireClickable: Boolean = true
    ): AccessibilityNodeInfo? {
        return try {
            traverseForText(root, keywords, requireClickable)
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error traversal node: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun traverseForText(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        requireClickable: Boolean
    ): AccessibilityNodeInfo? {
        // Periksa node saat ini
        val nodeText = node.text?.toString()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
        val combined = "$nodeText $nodeDesc".lowercase()

        // Strict guard against Clear Data (ai_task.md §15)
        if (SettingsNodeResolver.isDangerousClearData(combined, node.viewIdResourceName ?: "")) {
            return null
        }

        val matchesKeyword = keywords.any { keyword ->
            nodeText.contains(keyword, ignoreCase = true) ||
                    nodeDesc.contains(keyword, ignoreCase = true)
        }

        if (matchesKeyword && (!requireClickable || node.isClickable || node.isEnabled)) {
            return node
        }

        // Rekursif ke semua child nodes
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = traverseForText(child, keywords, requireClickable)
            if (result != null) return result
            child.recycle()
        }

        return null
    }

    // ===================================================
    // Fallback Heuristik: Resource ID & ClassName
    // ===================================================

    /**
     * Fallback jika pencarian via teks gagal.
     * Mencari tombol dialog standar Android via viewIdResourceName atau className.
     * Berguna untuk dialog konfirmasi "OK" yang mungkin tidak memiliki teks visible.
     */
    @Suppress("DEPRECATION")
    fun findConfirmButtonFallback(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            // Coba via resource ID dulu (paling reliable)
            val byId = root.findAccessibilityNodeInfosByViewId(ANDROID_BUTTON1_ID)
            if (!byId.isNullOrEmpty()) return byId.first()

            // Fallback: cari semua Button dan ambil yang clickable
            traverseForClassName(root, ANDROID_BUTTON_CLASSNAME)
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error fallback heuristic: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun traverseForClassName(
        node: AccessibilityNodeInfo,
        targetClassName: String
    ): AccessibilityNodeInfo? {
        if (node.className?.toString() == targetClassName && node.isClickable && node.isEnabled) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = traverseForClassName(child, targetClassName)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    // ===================================================
    // Helper: Safe click dengan null check
    // ===================================================

    /**
     * Klik node dengan aman — handle jika node sudah di-recycle atau null.
     * @return true jika klik berhasil
     */
    fun safeClick(node: AccessibilityNodeInfo?): Boolean {
        return try {
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (e: Exception) {
            Log.e(Constants.TAG_SERVICE, "Error saat klik node: ${e.message}")
            false
        }
    }
}
