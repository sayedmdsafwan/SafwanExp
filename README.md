# Safwan Exp — Expense Ledger

A lightweight, offline-first personal expense tracker and monthly budget planner for Android, built as a native WebView application.

<p align="center">
  <a href="https://sayedmdsafwan.github.io/SafwanExp/"><strong>🚀 Live Demo</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/sayedmdsafwan/SafwanExp/releases/latest/download/app-debug.apk"><strong>⬇ Download APK</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/sayedmdsafwan/SafwanExp/releases"><strong>All Releases</strong></a>
</p>

---

## Overview

Safwan Exp helps you track day-to-day expenses across multiple budgets ("tabs") and plan a monthly budget by separating fixed and variable costs against your income. All data is stored locally on the device — nothing is sent to a server. The app also supports exporting and restoring a full backup as a single JSON file.

- **Live demo:** [sayedmdsafwan.github.io/SafwanExp](https://sayedmdsafwan.github.io/SafwanExp/) — try it in your browser, no install needed
- **Package name:** `com.safwan.exp`
- **Platform:** Android (native WebView wrapper around a self-contained HTML/CSS/JS app)
- **Data storage:** Local only (`localStorage` inside the WebView) — fully offline
- **Language:** Kotlin (native shell) + HTML/CSS/JS (app UI and logic)

---

## Features

### Expense Tracker
- Create multiple independent tabs (e.g. "Main Budget", "Trip", "Business") — each with its own expense list and optional budget limit
- Add, edit, delete, and drag-to-reorder expense entries (amount, description, date)
- Live spent / remaining summary with a progress bar once a budget limit is set
- Rename or delete tabs at any time

### Monthly Budget Planner
- Set a monthly gross income
- Track **Fixed Costs** (rent, subscriptions, etc.) and **Variable Costs** (shopping, groceries, etc.) separately
- Automatic calculation of total cost and remaining balance
- Drag-to-reorder within each cost list

### Backup & Restore
- One-tap **Backup** exports all data (tabs, expenses, budgets, monthly planner, theme) to a single timestamped `.json` file
- **Restore** reads a previously exported `.json` file and replaces current data after confirmation
- Automatic reminder if no backup has been made in 7+ days
- On Android 10+, backups are saved via `MediaStore` (no storage permission required); Android 9 and below fall back to a direct file write with a runtime permission request
- The browser (live demo) version uses a plain file download/upload fallback for the same backup/restore flow

### UI/UX
- Dark and light theme, togglable, remembered across sessions
- Responsive layout — bottom navigation on mobile, sidebar navigation on wider/desktop screens
- Native Android status bar color/icon style syncs with the in-app theme

---

## Tech Stack

| Layer | Technology |
|---|---|
| Native shell | Kotlin, `WebView`, `AndroidX` |
| App UI/logic | HTML5, CSS3, vanilla JavaScript (no frameworks) |
| Data persistence | `localStorage` (in-app) + JSON file export/import (backup) |
| Build system | Gradle (Groovy DSL), Android Gradle Plugin |
| Min SDK | 24 (Android 7.0) |
| Target / Compile SDK | 34 |
| Live demo hosting | GitHub Pages |

---

## Project Structure

```
SafwanExp/
├── app/
│   ├── src/main/
│   │   ├── java/com/safwan/exp/
│   │   │   └── MainActivity.kt        # WebView host + native bridge (backup/restore, theme)
│   │   ├── assets/
│   │   │   └── index.html             # The entire app UI, styling, and logic
│   │   ├── res/
│   │   │   ├── drawable/              # Adaptive launcher icon layers
│   │   │   ├── mipmap-*/              # Legacy launcher icons (pre-Android 8 fallback)
│   │   │   └── values/                # Theme, colors, strings
│   │   └── AndroidManifest.xml
│   ├── build.gradle                   # App module build config
│   └── proguard-rules.pro
├── docs/                              # GitHub Pages source (live demo site)
│   ├── index.html                     # Landing page (light/dark toggle, demo + APK links)
│   └── app.html                       # Copy of the app, runnable directly in a browser
├── build.gradle                       # Project-level build config
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

### How the native bridge works

The web app (`assets/index.html`) talks to Android through a JavaScript interface named `AndroidBridge`, injected in `MainActivity.kt`:

| JS call | Native handler | Purpose |
|---|---|---|
| `AndroidBridge.saveFile(base64, filename)` | `saveToDownloads()` | Writes a backup JSON file to the device's Downloads folder |
| `AndroidBridge.openFilePicker()` | System file picker (`GetContent`) | Lets the user pick a `.json` backup file to restore |
| `AndroidBridge.setStatusBarStyle(isDark)` | `WindowInsetsController` | Keeps the system status bar icons readable against the current theme |

Results are passed back into JavaScript via `window.onAndroidSaveResult(...)` and `window.onAndroidFileContent(...)`. When the app runs in a plain browser (no `AndroidBridge` present, e.g. the live demo), the same code paths fall back to a standard file download/upload instead.

---

## Getting Started

### Try it without building anything
Open the [live demo](https://sayedmdsafwan.github.io/SafwanExp/) in any browser, or [download the latest APK](https://github.com/sayedmdsafwan/SafwanExp/releases/latest) and install it on an Android device.

### Build from source

**Prerequisites**
- [Android Studio](https://developer.android.com/studio) (recent stable version)
- JDK 8 or newer (bundled with Android Studio)
- An internet connection for the first Gradle sync (downloads the Gradle distribution and dependencies)

**Build & Run**
1. Clone or download this repository
2. Open Android Studio → **File → Open** → select the project folder
3. Wait for Gradle sync to finish
4. Connect a device or start an emulator, then press **Run ▶**

**Build a release APK**
**Build → Build Bundle(s) / APK(s) → Build APK(s)**, then locate the output via the notification that appears (typically `app/build/outputs/apk/debug/app-debug.apk` for a debug build).

---

## Backup File Format

A backup is a single `.json` file with the following shape:

```json
{
  "app": "Safwan Exp - Expense Tracker",
  "version": "2.0",
  "schema": 1,
  "createdAt": "ISO timestamp",
  "deviceTimestamp": 0,
  "data": {
    "appData": {
      "tabs": [ { "name": "...", "budget": "...", "items": [ /* expenses */ ] } ],
      "active": 0,
      "monthly": { "income": "...", "fixed": [ /* ... */ ], "variable": [ /* ... */ ] }
    },
    "theme": "dark",
    "meta": { "firstUse": "...", "lastBackup": "..." }
  }
}
```

Restoring validates this structure before applying it, and always asks for confirmation before overwriting existing data.

---

## Privacy

Safwan Exp does not collect, transmit, or store any data outside the user's own device. There is no analytics, no network calls, and no third-party services involved — all functionality works fully offline. Note that the live demo and the installed Android app store data separately (browser `localStorage` vs. the app's own storage) — they are not synced with each other.

---

## License

This project is currently unlicensed / private. Add a license of your choice (e.g. MIT) if you plan to share or open-source it.
