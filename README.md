<div align="center">

# 💸 Safwan Exp

**A calm, offline expense ledger and monthly budget planner for Android.**

<img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
<img alt="min SDK" src="https://img.shields.io/badge/min%20SDK-24%20(Android%207.0)-2FC48D?style=flat-square">
<img alt="License" src="https://img.shields.io/badge/license-MIT-2FC48D?style=flat-square">
<img alt="Offline" src="https://img.shields.io/badge/works-100%25%20offline-8C8371?style=flat-square">
<img alt="Latest release" src="https://img.shields.io/github/v/release/sayedmdsafwan/SafwanExp?style=flat-square&color=2FC48D">

<br><br>

<a href="https://github.com/sayedmdsafwan/SafwanExp/releases/latest/download/app-debug.apk">
  <img alt="Download the latest APK" src="https://img.shields.io/badge/%E2%AC%87%20Download%20APK-2FC48D?style=for-the-badge&logo=android&logoColor=white" height="42">
</a>

<br>

<a href="https://sayedmdsafwan.github.io/SafwanExp/"><strong>🌐 Try the live demo</strong></a> · [All releases](https://github.com/sayedmdsafwan/SafwanExp/releases) · [Report a bug](https://github.com/sayedmdsafwan/SafwanExp/issues)

</div>

---

Safwan Exp keeps two things that usually live in a spreadsheet: **what you actually spent**,
split across as many budgets as you need, and **what next month should look like**, split
into fixed and variable costs against your income.

Everything is stored on your phone. There is no account to create, no sync to trust, and
no server that could ever see your spending.

## ✨ Highlights

- **Multiple ledgers ("tabs")** — Main budget, a trip, a side business — each with its own list and optional spending limit.
- **Monthly planner** — income, fixed costs and variable costs, with the balance worked out live.
- **Drag to reorder** anything, anywhere: reorder expenses by priority or costs by importance.
- **Full backup as one JSON file** — export it anywhere through the system file picker, restore it on another phone.
- **Light and dark themes** that are remembered, on a layout that adapts from phone to desktop.
- **No internet permission at all.** The app cannot phone home even if it wanted to.

## 📲 Install

1. Open the [latest APK](https://github.com/sayedmdsafwan/SafwanExp/releases/latest/download/app-debug.apk) on your Android phone.
2. If Android asks, allow **Install unknown apps** for your browser or file manager.
3. Tap **Install**, then open **Safwan Exp**.

> This is a debug build published for easy testing, so Play Protect may show a warning.
> The app has no internet permission and the full source is in this repository.

Prefer not to install anything? The [live demo](https://sayedmdsafwan.github.io/SafwanExp/)
is the same app running in the browser — the demo and the installed app keep their data
separately.

## 🧾 What's inside

### Expense tracker
- Create independent tabs (e.g. *Main Budget*, *Trip*, *Business*), each with its own expense list and an optional budget limit.
- Add, edit, delete and drag-to-reorder entries — amount, description and date.
- Live spent / remaining summary with a progress bar once a limit is set.
- Rename or delete a tab at any time.

### Monthly budget planner
- Set your monthly gross income.
- Track **fixed costs** (rent, subscriptions) and **variable costs** (groceries, shopping) separately.
- Total cost and remaining balance update as you type; reorder either list by dragging.

### Backup and restore
- One tap **Backup** exports everything — tabs, expenses, budgets, the monthly planner and your theme — to a single timestamped `.json` file. You choose where it goes: Downloads, an SD card, Drive, or anywhere else the system picker can reach.
- **Restore** reads a `.json` file back after asking for confirmation, and validates what it finds before touching your data: amounts, real calendar dates, text length and field types are all checked, so a hand-edited or corrupted file cannot break or inject into the app.
- A gentle in-app reminder appears if you haven't backed up in seven days.

### Look and feel
- Dark and light theme, switchable, remembered between launches.
- Bottom navigation on phones, sidebar navigation on wider or desktop screens.
- The Android status bar icons follow the in-app theme.

## 🧱 How it is built

A single Kotlin activity hosts one self-contained HTML file in a `WebView`. The web app owns
every screen; Kotlin only provides what a browser page cannot do on Android — choosing where
a backup file is saved, opening the file picker, and tinting the status bar.

| Layer | Technology |
| --- | --- |
| Native shell | Kotlin, `AppCompatActivity`, `WebView`, AndroidX |
| App UI and logic | HTML5, CSS3, vanilla JavaScript (no frameworks, no build step) |
| Storage | `localStorage` inside the WebView |
| Backup | JSON file through the Storage Access Framework — no storage permission needed |
| Build | Gradle 8.6, Android Gradle Plugin 8.3.2, Kotlin 1.9.23 |
| Android | `minSdk 24` (7.0), `targetSdk` / `compileSdk` 34 |
| Live demo | GitHub Pages |

### The JavaScript bridge

| JavaScript call | What happens natively |
| --- | --- |
| `AndroidBridge.saveFile(base64, filename)` | Decodes the backup and opens the system **Save as** dialog (SAF), then reports back through `window.onAndroidSaveResult` |
| `AndroidBridge.openFilePicker()` | Opens the system file picker, checks the file really is a `.json`, and hands its bytes to `window.onAndroidFileContent` |
| `AndroidBridge.setStatusBarStyle(isDark)` | Switches the status bar icon colour to match the current theme |

In a plain browser (the live demo) none of these exist, and the same code paths fall back to
a normal file download and an `<input type="file">`.

## 📂 Project layout

```
SafwanExp/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/safwan/exp/
│       │   └── MainActivity.kt        # WebView host + AndroidBridge (backup/restore, status bar)
│       ├── assets/
│       │   └── index.html             # The entire app: UI, styling and logic
│       └── res/                       # Launcher icon, theme, colours, strings
├── docs/                              # GitHub Pages source
│   ├── index.html                     # Landing page
│   └── app.html                       # Copy of the app that runs in a browser
├── gradle/wrapper/                    # Gradle wrapper
├── build.gradle, settings.gradle, gradle.properties
├── LICENSE · README.md
```

## 🛠 Build from source

**Requirements:** [Android Studio](https://developer.android.com/studio) (recent stable
release), JDK 17 (the embedded JDK works). The first Gradle sync needs an internet
connection; after that everything is local.

```bash
git clone https://github.com/sayedmdsafwan/SafwanExp.git
cd SafwanExp
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
```

On Windows use `gradlew.bat`. In Android Studio: **File → Open** → select the folder, let
Gradle sync, then press **Run ▶**.

No signing config is included — configure your own keystore before `assembleRelease`.

## 🗄 Backup file format

```json
{
  "app": "Safwan Exp - Expense Tracker",
  "version": "2.1",
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

Restoring validates this structure — and every field inside it — before applying anything,
and always asks for confirmation before replacing existing data. Backups written by a newer
schema are rejected with a clear message rather than half-applied.

## 🔒 Privacy

Safwan Exp collects nothing, transmits nothing and stores nothing outside your own device.
There is no analytics, no network permission and no third-party service. Backups only leave
the phone when you export one and pick a destination yourself.

> The installed app and the browser demo keep separate data (the app's own storage versus
> your browser's `localStorage`) — they are not synced with each other.

## 📄 License

Released under the [MIT License](LICENSE). Use it, fork it, ship it.

---

<div align="center">
<sub>Made by <strong>Md. Safwan</strong> · Also on GitHub: <a href="https://github.com/sayedmdsafwan/Fuel-Metrics">Fuel Metrics</a> · <a href="https://github.com/sayedmdsafwan/SalatTime">Salat Time</a></sub>
</div>
