# Architecture — Platinum Cleaner

## Overview

Platinum Cleaner menggunakan **Capability-Based Architecture** yang memisahkan *deteksi kemampuan perangkat* dari *eksekusi pembersihan*.

Tujuan akhir: **Platinum Cleaner tidak membutuhkan Accessibility Service untuk menjadi aplikasi cleaner yang berguna.** Accessibility hanyalah salah satu adapter opsional.

> **Sprint V8 & V9 Update**: Real Per-App Automated Cleaning Engine & Full Inventory Integrity.
> Rekonstruksi total arsitektur inventaris aplikasi dan engine pembersihan otomatis:
> - Mengubah Start Clean menjadi engine per-aplikasi nyata (`PER_APP_AUTOMATED` saat Accessibility aktif, fallback `PER_APP_ASSISTED`).
> - Menjadikan `ACTION_CLEAR_APP_CACHE` sebagai secondary feature terpisah, menghentikan pembajakan tombol Start Clean oleh mode sistem.
> - Mempertahankan aplikasi ber-cache 0 B di inventaris (zero-cache retention) namun mengecualikannya dari target pembersihan otomatis.
> - Menegakkan **Hukum Besi (§15)**: Menolak keras tombol Clear Data / Hapus Data dalam kondisi apapun via `SettingsNodeResolver`.
> - Memperkenalkan `CleaningSession` sebagai state machine terstruktur, mendukung `skipCurrentApp()` dan `stopCleaning()`.
> - Menyediakan `CleaningSummarySheet` untuk transparansi penuh hasil verifikasi per-aplikasi.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                       │
│                                                                  │
│  DashboardScreen (Jetpack Compose)                               │
│    ├── HeroStorageCard & Primary Clean Action (Per-App Default)  │
│    ├── ViewAllInventorySheet (Real package discovery & search)   │
│    ├── CleaningProgressOverlay (Real-time progress, Skip, Stop)  │
│    ├── CleaningSummarySheet (Transparent verified results)       │
│    └── observes StateFlow dari DashboardViewModel                │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         ViewModel Layer                           │
│                                                                  │
│  DashboardViewModel                                              │
│    ├── AppCleanerRepository (Discovery, 0 B retention, Scanner)  │
│    ├── CleanSessionManager (State machine, batch targets)        │
│    └── CleaningOrchestrator (Execution coordinator)              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                        Platform Layer                             │
│                                                                  │
│  CleaningOrchestrator                                            │
│    ├── CapabilityResolver → resolveMode / isAccessibilityAvail   │
│    ├── AppInfoNavigator → Safe Settings activity navigation      │
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
│                         Service Layer                             │
│                                                                  │
│  PlatinumCleanerService                                          │
│    ├── SettingsNodeResolver (Strict Clear Data guard, scoring)   │
│    ├── AutomationProtocol (Semantics commands & results)         │
│    ├── AccessibilityNodeHelper (Multi-language recursive matcher)│
│    └── Bounded Return to Platinum Cleaner                        │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                         Domain Layer                              │
│                                                                  │
│  CleaningSession (Deterministic state machine: next, skip, stop) │
│  CleaningTarget (Per-package model: label, bytes, eligibility)   │
│  ScanResult (Authoritative single source of truth)               │
│  CleaningPlan / CleaningRequest / CleaningResult / AppCleanResult │
│  VerificationStatus (Honest outcome states: VERIFIED_SUCCESS...) │
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
│   │   ├── CleaningSession.kt      ← State machine pembersihan sekuensial
│   │   ├── CleaningTarget.kt       ← Target pembersihan individual
│   │   ├── CleaningUiState.kt      ← Representasi state UI pembersihan
│   │   ├── NavigationResult.kt     ← Status navigasi Settings
│   │   └── CleaningStrategy.kt     ← Interface untuk semua strategy
│   ├── inventory/
│   │   └── ScanResult.kt           ← Single Source of Truth inventaris
│   └── verification/
│       └── VerificationEngine.kt   ← Dual-path verification (aggregate + package-level)
│
├── platform/                        ← Android-specific implementations
│   └── cleaning/
│       ├── CapabilityResolver.kt              ← Deteksi kemampuan & resolusi mode
│       ├── CleaningOrchestrator.kt            ← Coordinator strategy + fallback + verify
│       ├── AppInfoNavigator.kt                ← Validasi paket & navigasi aman
│       ├── SystemCacheStrategy.kt             ← ACTION_CLEAR_APP_CACHE
│       ├── PerAppIntentStrategy.kt            ← ACTION_APPLICATION_DETAILS_SETTINGS
│       └── AccessibilityAutomationStrategy.kt ← Accessibility automated adapter
│
├── service/                         ← Accessibility & Session management
│   ├── PlatinumCleanerService.kt   ← Accessibility engine with bounded return
│   ├── SettingsNodeResolver.kt     ← Strict Clear Data rejection & multi-language cache detection
│   ├── AutomationProtocol.kt       ← AutomationCommand & AutomationCommandResult
│   ├── AccessibilityNodeHelper.kt  ← Traversal rekursif & safe click
│   ├── ServiceEventBus.kt          ← Reactive event bridge
│   └── CleanSessionManager.kt      ← State machine & batch targets queue
│
├── data/
│   └── AppCleanerRepository.kt     ← Full inventory discovery, 0 B retention, cache scanner
│
├── ui/
│   └── dashboard/
│       ├── DashboardScreen.kt          ← Main screen, ViewAllInventorySheet, CleaningSummarySheet
│       ├── DashboardComponents.kt      ← Reusable luxury components
│       ├── DashboardViewModel.kt       ← Lifecycle & state orchestration
│       ├── DashboardModels.kt          ← ScanState, InventorySummary, AppInfo
│       └── CleaningProgressOverlay.kt  ← Floating overlay with Skip & Stop confirmation
│
└── util/
    └── PermissionHelper.kt
```

---

## Core Engineering Invariants & Security Guardrails

### 1. Invariant Mutlak: Dilarang Keras Menghapus Data Pengguna (§15)
- Tombol **Clear Data / Hapus Data / Atur Penyimpanan / Kelola Ruang** MUTLAK DITOLAK dalam kondisi apapun oleh `SettingsNodeResolver.isDangerousClearData()` dan `AccessibilityNodeHelper`.
- Kata kunci berbahaya dari berbagai bahasa (EN, ID, ES, DE, IT, PT, FR) dan resource ID destruktif (`clear_data`, `clear_storage`) diblokir secara proaktif sebelum aksi klik dilakukan.

### 2. Inventaris Nyata & Retensi Aplikasi 0 B (§6, §27, §39)
- Semua aplikasi terinstal dipertahankan dalam inventaris, termasuk aplikasi yang ber-cache 0 B, sehingga pengguna dapat melihat status "0 B (Bersih)".
- Aplikasi ber-cache 0 B dikecualikan dari antrian target pembersihan otomatis guna mencegah navigasi sia-sia ke aplikasi yang sudah bersih.

### 3. Pemisahan Mode Pembersihan (§2, §16, §17, §37)
- **Start Clean** selalu memprioritaskan pembersihan nyata per-aplikasi (`PER_APP_AUTOMATED` bila Accessibility aktif, fallback ke `PER_APP_ASSISTED`).
- `ACTION_CLEAR_APP_CACHE` tidak lagi membajak tombol Start Clean dan hanya dijalankan bila pengguna memilih fitur pembersihan sistem secara eksplisit.

### 4. Navigasi Terverifikasi & Bounded Return (§10, §16, §45)
- Sebelum meluncurkan `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `AppInfoNavigator` memvalidasi keberadaan paket di `PackageManager`, status aktif (enabled), dan ketersediaan Activity resolver.
- Setelah pembersihan seluruh target selesai, service melakukan navigasi terikat (bounded) kembali ke aplikasi Platinum Cleaner.

### 5. Verifikasi Jujur & Transparansi UX (§14, §17, §31, §33)
- Verifikasi pasca-pembersihan menggunakan staged sampling (T0→T4) dengan query langsung dari `StorageStatsManager` tanpa merekayasa angka.
- Hasil akhir ditampilkan transparan melalui `CleaningSummarySheet` dan pesan status yang jujur (`VERIFIED_SUCCESS`, `VERIFIED_PARTIAL`, `NO_MEASURABLE_CHANGE`, `USER_SKIPPED`, `STOPPED`).
- Sesi pembersihan sepenuhnya berada di bawah kendali pengguna melalui tombol **Lewati (Skip)** dan dialog konfirmasi **Hentikan (Stop)**.
