# Cleaning Modes Architecture — Platinum Cleaner V8

Dokumen ini menjelaskan secara teknis tiga mode pembersihan yang didukung oleh Platinum Cleaner pada Sprint V8, alur resolusi kapabilitas, dan strategi verifikasinya.

---

## 1. Perbandingan Tiga Mode Pembersihan

| Parameter | Mode 1: SYSTEM_WIDE | Mode 2: PER_APP_ASSISTED | Mode 3: PER_APP_AUTOMATED |
|---|---|---|---|
| **Inten / Tindakan** | `StorageManager.ACTION_CLEAR_APP_CACHE` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + Accessibility |
| **Target Operasi** | Seluruh sistem Android (aggregate) | Package spesifik (`package:com.example.app`) | Package spesifik (`package:com.example.app`) |
| **Interaksi User** | Menekan konfirmasi di dialog sistem | Navigasi manual ke Penyimpanan → Hapus Cache | Otomatis via Accessibility Service |
| **UI Overlay** | "Penyimpanan Sistem Android" (Indeterminate) | "Membersihkan X dari Y" (Determinate) | "Membersihkan X dari Y" (Determinate) |
| **Verifikasi** | Akumulasi aggregate cache perangkat | Staged sampling per-package (T0→T4) | Staged sampling per-package (T0→T4) |
| **Prasyarat** | Didukung oleh ROM / OEM | Selalu didukung (Universal Android) | Izin Aksesibilitas aktif |

---

## 2. Resolusi Kapabilitas & Aturan Guard

Pemilihan mode diatur oleh `CapabilityResolver.kt` dan `CleaningOrchestrator.kt`:

1. **Aturan Single Targeted Clean (§54)**:
   - Jika pembersihan ditargetkan untuk **satu aplikasi spesifik** (misalnya menekan tombol "Bersihkan" pada Telegram di daftar aplikasi), sistem **DILARANG KERAS** menggunakan mode `SYSTEM_WIDE` (`ACTION_CLEAR_APP_CACHE`).
   - Sistem wajib memilih antara `PER_APP_AUTOMATED` (jika aksesibilitas aktif) atau `PER_APP_ASSISTED`.

2. **Aturan Smart Clean Umum (Batch)**:
   - Jika pengguna menekan tombol utama "Bersihkan Sekarang" di Dashboard:
     - Jika perangkat mendukung `SYSTEM_WIDE_CACHE_REQUEST`, mode `SYSTEM_WIDE` akan diprioritaskan sebagai satu operasi sistem tingkat tinggi.
     - Jika tidak didukung, sistem akan membuat `CleaningPlan` mode `PER_APP_AUTOMATED` atau `PER_APP_ASSISTED` yang memproses seluruh daftar aplikasi yang memiliki cache terukur tanpa pembatasan 5 aplikasi.

3. **Aturan Perlindungan Diri (§45)**:
   - Target pembersihan yang sama dengan package name aplikasi Platinum Cleaner (`context.packageName`) selalu ditolak dan difilter dari antrean.

---

## 3. Alur Verifikasi Hasil

Sesuai prinsip kejujuran data Android:

- **System-Wide Clean**:
  - Diukur menggunakan `queryAggregateCacheBytes(context)`.
  - Jika terjadi pengurangan cache: Dilaporkan sebagai `VERIFIED_SUCCESS` atau `VERIFIED_PARTIAL`.
  - Jika tidak ada perubahan angka (karena sistem OEM menunda atau tidak mengeksekusi): Dilaporkan sebagai `NO_MEASURABLE_CHANGE`, bukan kegagalan (`FAILED`).

- **Per-App Clean**:
  - Diukur secara presisi per-package menggunakan `StorageStatsManager.queryStatsForPackage`.
  - Menggunakan staged sampling (T0=0ms, T1=500ms, T2=1500ms, T3=3000ms, T4=5000ms).
  - Melakukan early-exit begitu cache berkurang untuk menghemat baterai dan mempercepat respons.
