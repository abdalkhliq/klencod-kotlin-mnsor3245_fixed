package com.klencod.mnsor3245

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.io.OutputStreamWriter

class MainActivity : Activity() {

    companion object {
        private const val REQ_EXPORT = 1001
        private const val REQ_IMPORT = 1002
    }

    private lateinit var webView: WebView
    private var pendingExport: String? = null

    // Exposed to the page as window.AndroidApp
    inner class AndroidApp {
        @JavascriptInterface
        fun exportBackup(json: String) {
            runOnUiThread {
                pendingExport = json
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "mnsor-backup.json")
                }
                startActivityForResult(intent, REQ_EXPORT)
            }
        }

        @JavascriptInterface
        fun importBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_IMPORT)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true   // needed for localStorage (saving the register)
            allowFileAccess = true
        }

        webView.addJavascriptInterface(AndroidApp(), "AndroidApp")

        // Show alert() and confirm() from the page as native dialogs
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("حسناً") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("نعم") { _, _ -> result?.confirm() }
                    .setNegativeButton("لا") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }

        webView.loadUrl("file:///android_asset/mnsor3245_fixed.html")
    }

    @Deprecated("Used for file picker results and WebView back navigation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return

        when (requestCode) {
            REQ_EXPORT -> {
                val json = pendingExport ?: return
                pendingExport = null
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        OutputStreamWriter(out, Charsets.UTF_8).use { it.write(json) }
                    }
                    Toast.makeText(this, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "تعذّر حفظ النسخة", Toast.LENGTH_LONG).show()
                }
            }
            REQ_IMPORT -> {
                try {
                    val text = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() } ?: ""
                    webView.evaluateJavascript(
                        "window.onBackupImported(" + JSONObject.quote(text) + ")", null
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "تعذّرت قراءة الملف", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Deprecated("Kept for WebView back navigation")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}