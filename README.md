# Safwan Exp — Android WebView Project

Package name: `com.safwan.exp` (unchanged, as requested).

## What was wrong (Restore bug)

Backup worked fine because saving just writes a `.json` file.
Restore was broken because the native file picker was launched with a
**strict MIME type filter** (e.g. `application/json`). Many Android file
managers — the stock Files app, OEM file managers, file managers inside
chat apps, etc. — tag downloaded `.json` files as `text/plain` or
`application/octet-stream` depending on how the file was saved. When the
picker filters strictly by `application/json`, those files show up
greyed out / not clickable, because the file's reported MIME type
doesn't match the filter — even though it really is your backup file.

## The fix

In `app/src/main/java/com/safwan/exp/MainActivity.kt`, `openFilePicker()`
now launches the picker with a permissive `"*/*"` MIME type (maximum
compatibility across file managers), and the code afterward checks that
the picked file's name actually ends in `.json` before handing it to the
web app. So you keep the safety check, but stop good backup files from
being invisible/unclickable in the picker.

No changes were needed on the HTML/JS side — `assets/index.html` is the
same app you already have. The bridge function names
(`AndroidBridge.saveFile`, `AndroidBridge.openFilePicker`,
`window.onAndroidSaveResult`, `window.onAndroidFileContent`) all match
exactly what your web app already calls.

## How to use this

1. Unzip this project.
2. Open Android Studio → **File → Open** → select the unzipped
   `SafwanExpWebView` folder.
3. Let Gradle sync (first sync will download Gradle 8.6 + AGP — needs
   internet).
4. Build → Generate Signed Bundle / APK (or just Run ▶ to test on a
   device/emulator).

If you already have your own existing Android Studio project for this
app and just want the fix, you only need to replace ONE file:

- `app/src/main/java/com/safwan/exp/MainActivity.kt`

Everything else in this zip (gradle files, icons, manifest, the html
asset) is provided so you have a complete, buildable project even if
you don't have the old one anymore.

## Notes

- Minimum SDK: 24 (Android 7.0). Target/compile SDK: 34.
- Saving backups uses `MediaStore` on Android 10+ (no permission
  needed) and falls back to direct file write + a runtime permission
  on Android 9 and below.
- Restoring reads the picked file via `ContentResolver`, so it works
  regardless of where the file physically lives (Downloads, Drive,
  WhatsApp folder, etc.) as long as some app exposes it through the
  system picker.
