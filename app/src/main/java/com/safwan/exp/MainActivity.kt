package com.safwan.exp

import android.annotation.SuppressLint
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Safwan Exp - Expense Tracker WebView wrapper.
 *
 * FIX NOTE (Backup bug):
 * Backups used to be written straight to the public Downloads folder via
 * MediaStore, with no picker shown to the user — so "Backup" would report
 * success without the person ever choosing where the file went. Restore,
 * meanwhile, already opened a proper system file picker so the user could
 * pick any location (file manager, Drive, etc).
 *
 * Backup now uses ACTION_CREATE_DOCUMENT (the Storage Access Framework),
 * which opens that same kind of picker and lets the user choose exactly
 * where to save the backup — Downloads, a Drive-backed folder, an SD
 * card, wherever they have access to. This requires no storage permission
 * on any supported Android version, so the legacy MediaStore/permission
 * path has been removed entirely.
 *
 * FIX NOTE (Restore bug, kept from before):
 * The file picker for restore uses a permissive wildcard MIME type
 * (asterisk, slash, asterisk) rather than "application/json", because
 * many file managers/providers mis-tag .json files as "text/plain" or
 * "application/octet-stream" - a strict MIME filter made valid backup
 * files show up greyed out / unselectable. The .json extension is still
 * verified in Kotlin before handing the file back to the WebView.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    /** Bytes queued for the next successful ACTION_CREATE_DOCUMENT result. */
    private var pendingBackupBytes: ByteArray? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            handlePickedFile(uri)
        }

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            handleBackupDestination(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        setupWebView()
        webView.loadUrl("file:///android_asset/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // required: app uses localStorage
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep everything inside the WebView; it's a single local page app.
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
    }

    // ─────────────────────────────────────────────
    // JS <-> Native bridge
    // ─────────────────────────────────────────────
    inner class AndroidBridge {

        @JavascriptInterface
        fun saveFile(base64Data: String, filename: String) {
            runOnUiThread {
                try {
                    // Queue the bytes, then let the user pick exactly where
                    // they want the backup saved via the system picker.
                    pendingBackupBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    createBackupLauncher.launch(filename)
                } catch (e: Exception) {
                    Log.e("AndroidBridge", "saveFile failed", e)
                    pendingBackupBytes = null
                    notifySaveResult("error", e.message ?: "Unknown error")
                }
            }
        }

        @JavascriptInterface
        fun openFilePicker() {
            runOnUiThread {
                try {
                    // "*/*" is intentional -> see FIX NOTE above the class.
                    filePickerLauncher.launch("*/*")
                } catch (e: Exception) {
                    Log.e("AndroidBridge", "openFilePicker failed", e)
                    Toast.makeText(this@MainActivity, "Could not open file picker", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun setStatusBarStyle(isDark: Boolean) {
            runOnUiThread {
                val insetsController = WindowCompat.getInsetsController(window, webView)
                // Light status bar icons look correct on a dark app background, and vice versa.
                insetsController.isAppearanceLightStatusBars = !isDark
            }
        }
    }

    private fun notifySaveResult(status: String, msg: String) {
        val safeMsg = msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        val js = "window.onAndroidSaveResult && window.onAndroidSaveResult('$status', '$safeMsg');"
        webView.evaluateJavascript(js, null)
    }

    // ─────────────────────────────────────────────
    // Saving backups (user picks the destination via SAF)
    // ─────────────────────────────────────────────
    private fun handleBackupDestination(uri: Uri?) {
        val bytes = pendingBackupBytes
        pendingBackupBytes = null

        if (uri == null) {
            // User backed out of the picker without choosing a location.
            notifySaveResult("cancelled", "")
            return
        }
        if (bytes == null) {
            notifySaveResult("error", "Backup data was lost, please try again")
            return
        }
        try {
            val resolver = contentResolver
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                ?: throw IOException("Could not open output stream")
            notifySaveResult("ok", "")
        } catch (e: Exception) {
            Log.e("AndroidBridge", "handleBackupDestination failed", e)
            notifySaveResult("error", e.message ?: "Unknown error")
        }
    }

    // ─────────────────────────────────────────────
    // Restoring backups (user picks the source via SAF)
    // ─────────────────────────────────────────────
    private fun handlePickedFile(uri: Uri?) {
        if (uri == null) return // user cancelled picker
        try {
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: ""
            if (!name.lowercase().endsWith(".json")) {
                Toast.makeText(this, "Only .json backup files can be restored", Toast.LENGTH_SHORT).show()
                return
            }
            val bytes = readBytes(uri)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val js = "window.onAndroidFileContent && window.onAndroidFileContent('$base64');"
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            Log.e("AndroidBridge", "handlePickedFile failed", e)
            Toast.makeText(this, "Could not read the selected file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun readBytes(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        val out = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
