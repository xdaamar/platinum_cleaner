package com.example.platinumcleaner.service

/**
 * AutomationCommand — Perintah otomasi semantik untuk Accessibility (ai_task.md §13).
 */
enum class AutomationCommand {
    VERIFY_TARGET_APP,
    FIND_STORAGE,
    OPEN_STORAGE,
    FIND_CLEAR_CACHE,
    CLICK_CLEAR_CACHE,
    RETURN_TO_CLEANER
}

/**
 * AutomationCommandResult — Hasil eksekusi terstruktur dari setiap perintah otomasi (ai_task.md §52).
 */
enum class AutomationCommandResult {
    SUCCESS,
    NOT_FOUND,
    CLEAR_DATA_BLOCKED,
    AMBIGUOUS,
    NOT_IN_TARGET_WINDOW,
    TIMEOUT,
    STOPPED
}
