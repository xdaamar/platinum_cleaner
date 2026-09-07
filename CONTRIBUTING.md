# Contributing to Platinum Cleaner

Thank you for considering a contribution to Platinum Cleaner. This document outlines the standards and process for submitting changes.

---

## 🛑 Non-Negotiable Rules

Before writing a single line of code, internalize these constraints. They are not preferences — they are the foundation of this project.

1. **Zero Network:** Never add `INTERNET` or any network-related permission or dependency. The app is and must remain 100% offline.
2. **AccessibilityService Scope:** The service must ONLY respond to `event.packageName == "com.android.settings"`. This check must remain the **first line** of `onAccessibilityEvent()`.
3. **No Third-Party Analytics/Tracking:** No Crashlytics, Firebase, Sentry, or any data-reporting library.
4. **No New Dependencies Without Discussion:** Open an issue first before adding a new library.

---

## 📐 Coding Standards

This project follows the principles in `/Docs/02_coding_standards.md`:

- **No Magic Strings/Numbers:** Every constant must live in `Constants.kt`.
- **Single Responsibility:** Each class/function does one thing well.
- **Dispatchers.IO for I/O:** Any operation involving `PackageManager`, `StorageStatsManager`, or file access must run on `Dispatchers.IO`.
- **Type Safety:** Use sealed classes and data classes instead of raw strings for state.
- **No `Thread.sleep()`:** Use `Handler.postDelayed()` or coroutines `delay()`.

---

## 🌿 Branching Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, deployable code. Protected. |
| `sprint/<name>` | Feature development (e.g. `sprint/analytics-screen`) |
| `fix/<issue>` | Bug fixes (e.g. `fix/samsung-clear-cache-timeout`) |
| `docs/<topic>` | Documentation only changes |

---

## 🔁 Pull Request Process

1. **Fork** the repository and create your branch from `main`.
2. **Write clean code** following the standards above.
3. **Test on a physical device** — the Accessibility Service cannot be tested in an emulator.
4. **Verify build:** Run `./gradlew assembleDebug` and ensure `BUILD SUCCESSFUL` with 0 errors and 0 warnings.
5. **Update documentation** if you change public-facing behavior.
6. **Open a PR** with a clear title and description:
   - What problem does it solve?
   - How was it tested?
   - Any known limitations or OEM-specific behavior?

---

## 🧪 Testing Priorities

Given the nature of the app, testing must include:

- [ ] **Happy path:** App reads cache, displays list, "Clean" button triggers auto-navigation.
- [ ] **Permission denied:** App shows onboarding bottom sheet gracefully (no crash).
- [ ] **User presses Back during navigation:** App recovers gracefully, shows snackbar.
- [ ] **OEM variation:** If possible, test on both Samsung One UI and a Pixel (vanilla Android).
- [ ] **Spam click:** Tapping "Clean" multiple times rapidly should not open multiple Settings screens.

---

## 💬 Getting Help

Open an [Issue](https://github.com/xdaamar/platinum_cleaner/issues) with a clear description of your question or bug. Include device model and Android version where relevant.
