<div align="center">

# ✦ Platinum Cleaner

**A privacy-first, offline-only Android cache cleaner built with Jetpack Compose.**

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](https://github.com/xdaamar/platinum_cleaner)
[![API Level](https://img.shields.io/badge/API-26%2B-blue?style=flat-square)](https://developer.android.com/about/versions/oreo)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-orange?style=flat-square)](https://developer.android.com/topic/architecture)

*Designed with a "Quiet Luxury" philosophy. Zero network permissions. Secure by design.*

</div>

---

## 📱 What It Does

Platinum Cleaner reads the cache size of every installed app on your device using Android's `StorageStatsManager` API — **entirely on-device, entirely offline** — and automates the navigation to "Clear Cache" in Android Settings via a strictly scoped `AccessibilityService`.

No ads. No analytics. No internet. No root required.

---

## 🎨 Design Philosophy — "Quiet Luxury"

The UI is built around a **minimal, premium aesthetic** inspired by high-end product design:

- **Color Palette:** `#F5F5F7` background, `#1D1D1F` foreground, `#E5E4E2` surfaces.
- **Typography:** Clean, geometric sans-serif with a strict 8pt grid system.
- **Motion:** Subtle, GPU-friendly `AnimatedVisibility` and `animateFloatAsState` transitions at 60fps.
- **No decorative noise.** Every element serves a function.

---

## 🔐 Security Architecture

This is the most important section of this README.

### Why Accessibility Service?

Android does not provide a public API for third-party apps to programmatically clear another app's cache (this was removed in Android 6). The only legitimate, non-root way to automate this is through the `AccessibilityService` API — the same mechanism used by accessibility tools for users with disabilities.

### How We Limit the Scope

We go to extreme lengths to prevent misuse:

**1. XML Configuration Lock (`res/xml/accessibility_service_config.xml`)**
```xml
android:packageNames="com.android.settings"
```
The service is declared to the Android OS as only active when the user is **inside the native Settings app**. Events from all other apps are never delivered.

**2. Runtime Guard (First Line of `onAccessibilityEvent`)**
```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event.packageName != Constants.SETTINGS_PACKAGE) return // IRON LAW
    // ...
}
```
Even if somehow a non-Settings event were delivered, it is immediately discarded.

**3. Session Manager Guard (`CleanSessionManager`)**
```kotlin
if (!CleanSessionManager.isActive) return
```
The service only takes any action when a session was **explicitly started by the user** tapping "Clean" in our UI. It cannot be triggered by external events.

**4. Zero Network Policy**
```xml
<!-- AndroidManifest.xml — no INTERNET permission exists -->
```
The app has no ability to send data anywhere.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                          │
│   DashboardScreen (Jetpack Compose)                      │
│   └── observes StateFlow from DashboardViewModel         │
├─────────────────────────────────────────────────────────┤
│                     ViewModel Layer                       │
│   DashboardViewModel (AndroidViewModel)                  │
│   ├── collects AppCleanerRepository (Flow)               │
│   └── collects ServiceEventBus (SharedFlow)              │
├──────────────────────────┬──────────────────────────────┤
│      Data Layer           │     Service Layer             │
│  AppCleanerRepository     │  PlatinumCleanerService       │
│  (StorageStatsManager     │  (AccessibilityService)       │
│   on Dispatchers.IO)      │   State Machine:              │
│                           │   IDLE → STORAGE →            │
│  PermissionHelper         │   CLEAR_CACHE →               │
│  (AppOpsManager)          │   CONFIRMING → DONE           │
├──────────────────────────┴──────────────────────────────┤
│               Communication Bridge                        │
│   ServiceEventBus (SharedFlow, replay=0)                 │
│   CleanSessionManager (Singleton session guard)          │
└─────────────────────────────────────────────────────────┘
```

---

## 🛠️ Build Instructions

### Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 or higher |
| Android Studio | Hedgehog (2023.1.1) or higher |
| Android SDK | API 34 (Android 14) |
| Android Build Tools | 34.x |
| Minimum Device API | 26 (Android 8.0 Oreo) |

### Build & Install

```bash
# Clone the repository
git clone https://github.com/xdaamar/platinum_cleaner.git
cd platinum_cleaner

# Install debug APK directly to connected device
./gradlew installDebug

# Launch the app
adb shell am start -n com.example.platinumcleaner/.MainActivity
```

### First-Time Setup on Device

After installing, the app requires two one-time permissions:

1. **Usage Access** — the app will show an onboarding bottom sheet. Tap "Enable in Settings" and grant access to Platinum Cleaner.
2. **Accessibility Service** — go to `Settings → Accessibility → Installed Apps → Platinum Cleaner` and enable it.

Both are required for the auto-clean feature to work.

---

## 📂 Project Structure

```
app/src/main/java/com/example/platinumcleaner/
├── Constants.kt                    # All constants (no magic strings)
├── MainActivity.kt
├── data/
│   └── AppCleanerRepository.kt     # StorageStatsManager on Dispatchers.IO
├── service/
│   ├── PlatinumCleanerService.kt   # AccessibilityService state machine
│   ├── ServiceEventBus.kt          # SharedFlow bridge (no memory leak)
│   └── CleanSessionManager.kt      # Session guard (anti double-execution)
├── ui/
│   ├── dashboard/
│   │   ├── DashboardScreen.kt      # Main Compose UI
│   │   ├── DashboardComponents.kt  # Reusable UI components
│   │   ├── DashboardViewModel.kt   # MVVM ViewModel
│   │   └── DashboardModels.kt      # Data models & UiState
│   └── theme/
│       ├── Theme.kt                # Edge-to-edge transparent system bars
│       ├── Color.kt                # Quiet Luxury color tokens
│       ├── Type.kt                 # Typography scale
│       └── Dimens.kt               # 8pt grid system
└── util/
    └── PermissionHelper.kt         # Usage Access permission utilities
```

---

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.
