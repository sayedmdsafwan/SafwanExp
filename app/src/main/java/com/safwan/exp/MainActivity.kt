package com.safwan.exp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Safwan Exp - Expense Tracker WebView wrapper.
 *
 * FIX NOTE (Restore bug):
 * The previous implementation opened the file picker using a strict MIME
 * type (e.g. "application/json"). Many Android file managers (stock
 * "Files" app, OEM skins, file managers bundled with chat apps, etc.)
 * mis-tag downloaded .json files as "text/plain" or
 * "application/octet-stream" depending on how they were saved. When the
 * picker intent filters strictly by "application/json", those files show
 * up greyed out / unselectable, because their reported MIME type doesn't
 * match.
 *
 * The fix here uses ACTION_GET_CONTENT with a permissive wildcard mime type
 * (maximum compatibility across file managers/providers), then verifies
 * the picked file actually has a .json extension in Kotlin code before
 * handing it back to the WebView. This keeps the safety check but stops
 * good files from being invisible/unclickable in the picker.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingSaveResultOk = false

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            handlePickedFile(uri)
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
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveToDownloads(filename, bytes)
                    notifySaveResult(true, "")
                } catch (e: Exception) {
                    Log.e("AndroidBridge", "saveFile failed", e)
                    notifySaveResult(false, e.message ?: "Unknown error")
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

    private fun notifySaveResult(ok: Boolean, msg: String) {
        val status = if (ok) "ok" else "error"
        val safeMsg = msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        val js = "window.onAndroidSaveResult && window.onAndroidSaveResult('$status', '$safeMsg');"
        webView.evaluateJavascript(js, null)
    }

    // ─────────────────────────────────────────────
    // Saving backups (scoped storage aware)
    // ─────────────────────────────────────────────
    private fun saveToDownloads(filename: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
                ?: throw IOException("Could not create file in Downloads")
            resolver.openOutputStream(uri).use { out: OutputStream? ->
                out ?: throw IOException("Could not open output stream")
                out.write(bytes)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            // Android 9 and below: legacy direct file write to public Downloads.
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw IOException("Storage permission not granted")
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { it.write(bytes) }
        }
    }

    // ─────────────────────────────────────────────
    // Restoring backups
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
