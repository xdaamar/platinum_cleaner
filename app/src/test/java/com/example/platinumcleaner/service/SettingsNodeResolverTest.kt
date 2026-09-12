package com.example.platinumcleaner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SettingsNodeResolverTest — Unit tests for SettingsNodeResolver and AutomationProtocol (ai_task.md V9 §13, §14, §15, §50, §60).
 *
 * Verifies:
 * - ABSOLUTE INVARIANT (§15): Clear Data / Hapus Data is NEVER permitted and always rejected.
 * - Multi-language Clear Cache semantic matching (EN, ID, ES, DE, IT, FR).
 * - Storage menu detection.
 * - Target app window verification.
 */
class SettingsNodeResolverTest {

    @Test
    fun testStrictClearDataSafetyInvariant_NeverAllowsClearData() {
        val dangerousTexts = listOf(
            "Clear data",
            "Hapus data",
            "Delete data",
            "Clear storage",
            "Hapus penyimpanan",
            "Atur penyimpanan",
            "Manage storage",
            "Manage space",
            "Kelola ruang",
            "Kelola penyimpanan",
            "Borrar datos",
            "Daten löschen",
            "Eliminar datos",
            "Svuota memoria",
            "Limpar dados"
        )

        for (text in dangerousTexts) {
            val eval = SettingsNodeResolver.evaluateNode(
                text = text,
                contentDescription = null,
                viewIdResourceName = null
            )
            assertEquals("Teks berbahaya '$text' HARUS diblokir!", SettingsNodeResolver.EvaluationResult.ClearDataBlocked, eval)
            assertTrue("isDangerousClearData harus true untuk '$text'", SettingsNodeResolver.isDangerousClearData(text.lowercase()))
        }
    }

    @Test
    fun testDangerousResourceId_IsBlocked() {
        val eval1 = SettingsNodeResolver.evaluateNode(
            text = "Button",
            contentDescription = null,
            viewIdResourceName = "com.android.settings:id/clear_data_button"
        )
        assertEquals(SettingsNodeResolver.EvaluationResult.ClearDataBlocked, eval1)

        val eval2 = SettingsNodeResolver.evaluateNode(
            text = "Button",
            contentDescription = null,
            viewIdResourceName = "com.android.settings:id/clear_storage"
        )
        assertEquals(SettingsNodeResolver.EvaluationResult.ClearDataBlocked, eval2)
    }

    @Test
    fun testSafeClearCacheMultiLanguageMatching() {
        val safeCacheTexts = listOf(
            "Clear cache",
            "Hapus cache",
            "Hapus memori",
            "Bersihkan cache",
            "Bersihkan memori",
            "Cache löschen",
            "Borrar caché",
            "Borrar cache",
            "Svuota cache",
            "Limpar cache",
            "Vider le cache"
        )

        for (text in safeCacheTexts) {
            val eval = SettingsNodeResolver.evaluateNode(
                text = text,
                contentDescription = null,
                viewIdResourceName = null
            )
            assertTrue("Teks aman '$text' harus dikenali sebagai ClearCacheCandidate", eval is SettingsNodeResolver.EvaluationResult.ClearCacheCandidate)
            assertFalse("Teks aman '$text' TIDAK boleh dianggap berbahaya", SettingsNodeResolver.isDangerousClearData(text.lowercase()))
        }
    }

    @Test
    fun testClearCacheResourceIdMatching() {
        val eval = SettingsNodeResolver.evaluateNode(
            text = null,
            contentDescription = null,
            viewIdResourceName = "com.android.settings:id/clear_cache_button"
        )
        assertTrue(eval is SettingsNodeResolver.EvaluationResult.ClearCacheCandidate)
        val candidate = eval as SettingsNodeResolver.EvaluationResult.ClearCacheCandidate
        assertEquals(1.0f, candidate.confidence, 0.001f)
        assertEquals("RESOURCE_ID", candidate.matchType)
    }

    @Test
    fun testStorageMenuKeywords() {
        val storageKeywords = listOf(
            "Storage",
            "Penyimpanan",
            "Storage & cache",
            "Penyimpanan & cache",
            "Penggunaan penyimpanan",
            "Almacenamiento",
            "Speicher"
        )

        for (text in storageKeywords) {
            val eval = SettingsNodeResolver.evaluateNode(
                text = text,
                contentDescription = null,
                viewIdResourceName = null
            )
            assertTrue("Keyword '$text' harus dikenali sebagai StorageMenuCandidate", eval is SettingsNodeResolver.EvaluationResult.StorageMenuCandidate)
        }
    }

    @Test
    fun testVerifyTargetAppWindow() {
        val texts = listOf("Telegram", "App Info", "Notifications", "Storage & cache")
        assertTrue(SettingsNodeResolver.verifyTargetAppWindow("Telegram", texts))
        assertTrue(SettingsNodeResolver.verifyTargetAppWindow("telegram", texts))
        assertFalse(SettingsNodeResolver.verifyTargetAppWindow("WhatsApp", texts))
    }

    @Test
    fun testAutomationProtocolEnums() {
        assertEquals(6, AutomationCommand.values().size)
        assertEquals(7, AutomationCommandResult.values().size)
        assertTrue(AutomationCommand.values().contains(AutomationCommand.CLICK_CLEAR_CACHE))
        assertTrue(AutomationCommandResult.values().contains(AutomationCommandResult.CLEAR_DATA_BLOCKED))
    }
}
