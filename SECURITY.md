# Security — Platinum Cleaner

## Core Security Invariant

> **Platinum Cleaner can never intentionally clear application DATA.**

Hanya **cache** yang boleh diminta untuk dihapus. User data tidak boleh tersentuh.

---

## Self-Protection Invariant (Sprint V8)

> **Platinum Cleaner can NEVER target its own package for cleaning or modification.**

Sesuai ai_task.md §45:
- Sebelum mengeksekusi pembersihan single-app (`initiateCleanForApp`), dilakukan pengecekan `packageName == context.packageName`. Jika cocok, eksekusi segera dibatalkan dengan log peringatan.
- Dalam pembersihan batch, package aplikasi Platinum Cleaner difilter keluar dari antrean target.

---

## Zero Network Policy

Aplikasi ini **tidak memiliki izin INTERNET**. Tidak ada:
- Network request
- Analytics / telemetry
- Remote logging
- Screenshot upload
- UI tree transmission

```xml
<!-- AndroidManifest.xml — TIDAK ADA izin INTERNET atau ACCESS_NETWORK_STATE -->
<!-- Verifikasi manifest: 100% offline application -->
```

---

## Accessibility Service Security Boundary

AccessibilityService adalah API sensitif. Kami membatasinya secara berlapis:

### Layer 1 — XML Declaration
```xml
android:packageNames="com.android.settings"
```
Service hanya menerima events dari Settings app. Events dari semua app lain tidak pernah dikirim ke service ini.

### Layer 2 — Runtime Guard (Iron Law)
```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // BARIS PERTAMA — selalu
    if (event.packageName != Constants.SETTINGS_PACKAGE) return
}
```

### Layer 3 — Session Guard
```kotlin
if (!CleanSessionManager.isActive) return
```
Service tidak melakukan apa-apa tanpa sesi yang secara eksplisit dimulai user.

### Layer 4 — Optional by Design
Accessibility adalah **Priority 3** — hanya digunakan jika user mengaktifkannya secara eksplisit. Aplikasi berfungsi penuh tanpa Accessibility.

---

## Forbidden Operations

Service dan semua strategy dilarang keras melakukan:

| Operasi | Alasan |
|---|---|
| `pm clear <package>` | Menghapus data user, bukan hanya cache |
| `rm -rf /data/data/<package>/cache` | Root operation, destructive |
| `ACTION_CLEAR_PACKAGE_DATA` | Data deletion |
| Click "Clear Storage" / "Clear Data" | Hapus semua data user |
| Click "Force Stop" | Di luar scope |
| Click "Uninstall" | Di luar scope |
| OCR / screenshot capture | Privacy violation |
| Accessibility tree ke network | Privacy + security violation |
| Targeted clean on own package | Self-protection violation |

---

## Permission Model

| Permission | Scope | Dapat Dicabut User |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Baca cache statistics saja | Ya (AppOps) |
| `QUERY_ALL_PACKAGES` | Daftar packages untuk scanner | Tidak (normal permission) |
| Accessibility Service | Opsional — automasi klik di Settings saja | Ya (any time) |

---

## Cache Statistics Integrity

`StorageStatsManager` adalah API resmi Android yang:
- Berjalan sepenuhnya on-device
- Tidak mengirim data ke mana pun
- Mengembalikan estimasi aktual dari sistem berkas OS
- Dapat berubah saat sistem melakukan background cleanup

UI tidak pernah menjanjikan angka pasti — selalu menggunakan wording jujur "potentially reclaimable".
