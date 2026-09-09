# Architecture — Platinum Cleaner

## Overview

Platinum Cleaner menggunakan **Capability-Based Architecture** yang memisahkan *deteksi kemampuan perangkat* dari *eksekusi pembersihan*.

Tujuan akhir: **Platinum Cleaner tidak membutuhkan Accessibility Service untuk menjadi aplikasi cleaner yang berguna.** Accessibility hanyalah salah satu adapter opsional.

> **Sprint 7 Update**: Physical Execution & Verification Reliability Fix — memperbaiki root cause kegagalan di device fisik Galaxy A55 (Samsung One UI / Android 14). Tambah staged verification T0-T4, expanded VerificationStatus states, structured debug logging, dan capability preflight.

---


## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                       │
│                                                                  │
│  DashboardScreen (Jetpack Compose)                               │
│    └── observes StateFlow dari DashboardViewModel                │
│         └── onAppResumed() → triggerSmartClean() → onSnackbar   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         ViewModel Layer                           │
│                                                                  │
│  DashboardViewModel                                              │
│    ├── AppCleanerRepository (Scanner — read-only)                │
│    └── CleaningOrchestrator (Brain cleaning engine)              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                        Platform Layer                             │
│                                                                  │
│  CleaningOrchestrator                                            │
│    ├── CapabilityResolver → mendeteksi capability perangkat      │
│    └── Strategy Selection (priority order):                      │
│         1. SystemCacheStrategy (ACTION_CLEAR_APP_CACHE)         │
│         2. PerAppIntentStrategy (ACTION_APPLICATION_DETAILS)    │
│         3. [Optional] AccessibilityAdapter (user-enabled)       │
│                                                                  │
│  VerificationEngine                                              │
│    └── before/after comparison via StorageStatsManager          │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         Domain Layer                              │
│                                                                  │
│  CleaningStrategy (interface)                                    │
│  CleaningRequest / CleaningResult / AppCleanResult               │
│  CleaningCapability (enum)                                       │
│  VerificationStatus (enum)                                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
app/src/main/java/com/example/platinumcleaner/
├── Constants.kt
├── MainActivity.kt
│
├── domain/                          ← Pure Kotlin, no Android dependency
│   ├── cleaning/
│   │   ├── CleaningCapability.kt   ← Enum kemampuan perangkat
│   │   ├── CleaningRequest.kt      ← Input ke Orchestrator
│   │   ├── CleaningResult.kt       ← Output Orchestrator + VerificationStatus
│   │   └── CleaningStrategy.kt     ← Interface untuk semua strategy
│   └── verification/
│       └── VerificationEngine.kt   ← Before/after comparison logic
│
├── platform/                        ← Android-specific implementations
│   └── cleaning/
│       ├── CapabilityResolver.kt   ← Deteksi capability (tidak eksekusi)
│       ├── CleaningOrchestrator.kt ← Brain: select strategy + fallback + verify
│       ├── SystemCacheStrategy.kt  ← Priority 1: ACTION_CLEAR_APP_CACHE
│       └── PerAppIntentStrategy.kt ← Priority 2: ACTION_APPLICATION_DETAILS_SETTINGS
│
├── service/                         ← Accessibility layer (optional)
│   ├── PlatinumCleanerService.kt   ← Accessibility adapter (optional)
│   ├── AccessibilityNodeHelper.kt  ← Multi-language node finder
│   ├── ServiceEventBus.kt          ← Reactive event bridge
│   └── CleanSessionManager.kt      ← Strategy-agnostic state machine
│
├── data/
│   └── AppCleanerRepository.kt     ← Scanner: StorageStatsManager (read-only)
│
├── ui/
│   └── dashboard/
│       ├── DashboardScreen.kt
│       ├── DashboardComponents.kt
│       ├── DashboardViewModel.kt
│       ├── DashboardModels.kt
│       └── CleaningProgressOverlay.kt
│
└── util/
    └── PermissionHelper.kt
```

---

## Strategy Priority

| Priority | Strategy | Trigger | Verification |
|---|---|---|---|
| 1 | `SystemCacheStrategy` | `ACTION_CLEAR_APP_CACHE` | Before/after via `StorageStatsManager` |
| 2 | `PerAppIntentStrategy` | `ACTION_APPLICATION_DETAILS_SETTINGS` | Before/after on `ON_RESUME` |
| 3 | `AccessibilityAdapter` | Automated click (optional, user-enabled) | Before/after on `ON_RESUME` |
| — | Manual fallback | Snackbar instruction | None |

---

## Session State Machine

```
Idle → Planning → ResolvingCapability → Executing → WaitingForResume → Verifying → Completed
                                          ↓
                                        Failed → FallbackAvailable?
                                                   ├── YES → next strategy
                                                   └── NO  → user informed
```

---

## Verification Flow

Setiap hasil cleaning WAJIB diverifikasi. Tidak ada hardcoded success.

```
triggerSmartClean()
      ↓
Snapshot beforeBytes (dari Scanner)
      ↓
Orchestrator.execute() → strategy launches intent
      ↓
State: PENDING_VERIFICATION
      ↓
User kembali (ON_RESUME) → onAppResumed()
      ↓
Orchestrator.verifyAfterResume()
      ↓
VerificationEngine.verify() — bounded retry (max 3x, 1.5s interval)
      ↓
classify(before, after):
  ≥ 90% reduction → VERIFIED_SUCCESS
  10–89% reduction → PARTIAL_SUCCESS
  < 10% reduction  → NO_CHANGE
  error             → UNKNOWN
      ↓
UI update dengan angka nyata (bukan estimasi)
```

---

## Open-Source Extension Points

Contributor dapat menambahkan strategy baru **tanpa mengubah** UI, Orchestrator, Scanner, atau domain models:

```kotlin
class MyOemSpecificStrategy : CleaningStrategy {
    override val capability = CleaningCapability.ACCESSIBILITY_AUTOMATION
    override fun isSupported(context: Context): Boolean { ... }
    override suspend fun execute(context: Context, request: CleaningRequest): CleaningResult { ... }
}

// Daftarkan ke Orchestrator:
val orchestrator = CleaningOrchestrator(
    strategies = listOf(SystemCacheStrategy(), PerAppIntentStrategy(), MyOemSpecificStrategy())
)
```
