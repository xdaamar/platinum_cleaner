# Architecture — Platinum Cleaner

## Overview

Platinum Cleaner menggunakan **Capability-Based Architecture** yang memisahkan *deteksi kemampuan perangkat* dari *eksekusi pembersihan*.

Tujuan akhir: **Platinum Cleaner tidak membutuhkan Accessibility Service untuk menjadi aplikasi cleaner yang berguna.** Accessibility hanyalah salah satu adapter opsional.

> **Sprint V8 Update**: Cleaning Engine Realignment & Full Inventory Rebuild — Rekonstruksi total arsitektur inventaris aplikasi dan engine pembersihan. Memperbaiki pemisahan semantik antara System-Wide Mode dan Per-App Mode, menghilangkan batasan buatan (5-app limit), mengisolasi error scanner per-package, serta mengimplementasikan verifikasi dual-path (aggregate vs package-level).

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                       │
│                                                                  │
│  DashboardScreen (Jetpack Compose)                               │
│    ├── HeroStorageCard & Action Buttons                          │
│    ├── ViewAllInventorySheet (Real package modal bottom sheet)    │
│    ├── CleaningProgressOverlay (Mode-dependent real-time UI)     │
│    └── observes StateFlow dari DashboardViewModel                │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         ViewModel Layer                           │
│                                                                  │
│  DashboardViewModel                                              │
│    ├── AppCleanerRepository (Discovery & Cache Scanner)          │
│    ├── CleanSessionManager (State machine & batch queue)         │
│    └── CleaningOrchestrator (Execution coordinator)              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                        Platform Layer                             │
│                                                                  │
│  CleaningOrchestrator                                            │
│    ├── CapabilityResolver → resolveMode / isSystemWideAvailable  │
│    ├── Strategy Execution via CleaningPlan:                      │
│    │    ├── SystemCacheStrategy (StorageManager.ACTION_CLEAR)    │
│    │    ├── PerAppIntentStrategy (ACTION_APPLICATION_DETAILS)   │
│    │    └── AccessibilityAutomationStrategy                      │
│    │                                                             │
│    └── VerificationEngine (Dual-path verification)               │
│         ├── System-wide: queryAggregateCacheBytes                │
│         └── Per-app: staged sampling (T0→T4, max 5000ms)         │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         Domain Layer                              │
│                                                                  │
│  CleaningPlan (Mode: SYSTEM_WIDE, PER_APP_ASSISTED, AUTOMATED)   │
│  CleaningStrategy (Interface)                                    │
│  CleaningRequest / CleaningResult / AppCleanResult               │
│  CleaningCapability (Enum priority order)                        │
│  VerificationStatus (Honest outcome states)                      │
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
│   │   ├── CleaningMode.kt         ← SYSTEM_WIDE, PER_APP_ASSISTED, PER_APP_AUTOMATED
│   │   ├── CleaningPlan.kt         ← Rencana pembersihan eksplisit
│   │   ├── CleaningRequest.kt      ← Input ke Orchestrator
│   │   ├── CleaningResult.kt       ← Output Orchestrator + VerificationStatus
│   │   └── CleaningStrategy.kt     ← Interface untuk semua strategy
│   └── verification/
│       └── VerificationEngine.kt   ← Dual-path verification (aggregate + package-level)
│
├── platform/                        ← Android-specific implementations
│   └── cleaning/
│       ├── CapabilityResolver.kt              ← Deteksi kemampuan & resolusi mode
│       ├── CleaningOrchestrator.kt            ← Coordinator strategy + fallback + verify
│       ├── SystemCacheStrategy.kt             ← ACTION_CLEAR_APP_CACHE
│       ├── PerAppIntentStrategy.kt            ← ACTION_APPLICATION_DETAILS_SETTINGS
│       └── AccessibilityAutomationStrategy.kt ← Accessibility automated adapter
│
├── service/                         ← Accessibility & Session management
│   ├── PlatinumCleanerService.kt   ← Accessibility adapter (optional)
│   ├── AccessibilityNodeHelper.kt  ← Multi-language node finder
│   ├── ServiceEventBus.kt          ← Reactive event bridge
│   └── CleanSessionManager.kt      ← Strategy-agnostic state machine & queue
│
├── data/
│   └── AppCleanerRepository.kt     ← Full inventory discovery & robust cache scanner
│
├── ui/
│   └── dashboard/
│       ├── DashboardScreen.kt          ← Main screen + ViewAllInventorySheet
│       ├── DashboardComponents.kt      ← Reusable luxury components
│       ├── DashboardViewModel.kt       ← Lifecycle & state orchestration
│       ├── DashboardModels.kt          ← ScanState, InventorySummary, AppInfo
│       └── CleaningProgressOverlay.kt  ← Mode-aware floating overlay
│
└── util/
    └── PermissionHelper.kt
```

---

## Sprint V8 Architectural Pillars

### 1. Inventory Truth & Real Package Data
- **Discovery**: `PackageManager.getInstalledPackages(PackageManager.GET_META_DATA)` mengembalikan seluruh paket asli di perangkat.
- **Deduplikasi**: Paket dideduplikasi berdasarkan `packageName`.
- **Klasifikasi**: Memisahkan aplikasi pengguna (`FLAG_SYSTEM == 0`) dari paket framework sistem (`FLAG_SYSTEM != 0`) dan memverifikasi `isLaunchable` via `getLaunchIntentForPackage`.
- **UI Inventory**: Tidak ada dialog kosong; `ViewAllInventorySheet` menyediakan pencarian real-time dan filter chips (Semua, Pengguna, Sistem).

### 2. Isolated Cache Scanner
- Pengambilan cache per aplikasi menggunakan `StorageStatsManager.queryStatsForPackage`.
- Setiap kegagalan paket (`SecurityException`, `NameNotFoundException`, `IOException`) diisolasi dalam blok try/catch individual sehingga tidak membatalkan pemindaian keseluruhan.
- Status pemindaian eksplisit: `SCANNING`, `SUCCESS`, `PARTIAL`, `EMPTY`, `PERMISSION_REQUIRED`.
- Total cache dihitung dari akumulasi aktual paket yang berhasil diukur.

### 3. Semantic Cleaning Mode Separation
- **System-Wide Mode**: Meminta pembersihan cache seluruh sistem melalui `StorageManager.ACTION_CLEAR_APP_CACHE`. Merupakan 1 operasi sistem tunggal, tidak berpura-pura menjadi antrean penghapusan per-aplikasi.
- **Per-App Assisted Mode**: Membuka halaman Info Aplikasi spesifik via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` dengan package URI untuk dieksekusi oleh pengguna.
- **Per-App Automated Mode**: Membantu navigasi dan penekanan tombol Hapus Cache melalui Accessibility Service jika diaktifkan secara eksplisit.
- **Targeted Single App Guard**: Pembersihan aplikasi target spesifik dilarang keras menggunakan mode system-wide.

### 4. Dual-Path Verification Engine
- **System-Wide Verification**: Menghitung delta total aggregate cache sebelum dan sesudah tindakan (`queryAggregateCacheBytes`). Jika pengurangan tidak terukur, dilaporkan secara jujur sebagai `NO_MEASURABLE_CHANGE` (bukan `FAILED`).
- **Per-App Verification**: Menggunakan staged sampling (T0=0ms, T1=500ms, T2=1500ms, T3=3000ms, T4=5000ms) dengan early-exit saat pengurangan terdeteksi.

### 5. Removal of Artificial Limitations
- Menghapus batasan `take(5)` dari domain dan session manager.
- Antrean batch dapat memproses seluruh aplikasi yang memenuhi syarat secara sekuensial.
- Sesi pembersihan mendukung pembatalan (`cancelSession()`) kapan saja oleh pengguna.
