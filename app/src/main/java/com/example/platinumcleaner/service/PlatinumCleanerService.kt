package com.example.platinumcleaner.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.platinumcleaner.Constants

/**
 * PlatinumCleanerService — AccessibilityService untuk otomasi navigasi Settings.
 *
 * =====================================
 * SECURITY CRITICAL — 03_security_protocols.md
 * =====================================
 *
 * HUKUM BESI:
 * Service ini HANYA boleh merespons event dari package "com.android.settings".
 * Seluruh event dari aplikasi lain (perbankan, pesan, browser, dsb.) WAJIB diabaikan
 * untuk mencegah Clickjacking / UI Redressing.
 *
 * Sprint 2: Skeleton — hanya log event yang diterima dari Settings.
 * Sprint 3: Implementasi navigasi otomatis (Settings -> Apps -> Storage -> Clear Cache).
 */
class PlatinumCleanerService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Konfigurasi service via code (backup dari accessibility_service_config.xml)
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100L
        }

        Log.d(Constants.TAG_SERVICE, "PlatinumCleanerService tersambung dan aktif")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // ==========================================
        // HUKUM BESI KEAMANAN (03_security_protocols.md)
        // Abaikan SEMUA event dari package selain Settings.
        // Ini mencegah clickjacking dari aplikasi perbankan, browser, dsb.
        // ==========================================
        if (event.packageName != Constants.SETTINGS_PACKAGE) {
            return // Diam dan abaikan — tidak ada interaksi di luar Settings
        }

        // Sprint 2: Hanya log event untuk verifikasi service berjalan dengan benar
        Log.d(
            Constants.TAG_SERVICE,
            "Event diterima di Settings: type=${event.eventType}, class=${event.className}"
        )

        // Sprint 3: Navigasi otomatis ke Clear Cache akan diimplementasikan di sini
    }

    override fun onInterrupt() {
        Log.d(Constants.TAG_SERVICE, "PlatinumCleanerService diinterupsi oleh sistem")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Constants.TAG_SERVICE, "PlatinumCleanerService dihentikan")
    }
}
