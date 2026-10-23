package com.example.mydm

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var detectedBtn: Button
    private val found = LinkedHashSet<String>()
    private val media = Regex("""\.(mp4|webm|mkv|mov|3gp|avi|flv|mp3|m4a|aac|ogg|flac|wav)(\?.*)?$""", RegexOption.IGNORE_CASE)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }

        urlBar = EditText(this).apply {
            hint = "輸入網址或搜尋"; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> load(text.toString()); true }
        }
        val top = LinearLayout(this)
        top.addView(urlBar, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Button(this).apply { text = "前往"; setOnClickListener { load(urlBar.text.toString()) } })

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                found.clear(); refresh(); urlBar.setText(u)
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                if (media.containsMatchIn(u)) runOnUiThread { found.add(u); refresh() }
                return null
            }
        }
        web.setDownloadListener { u, _, _, _, _ -> download(u) }

        detectedBtn = Button(this).apply { setOnClickListener { showDetected() } }
        val bottom = LinearLayout(this)
        bottom.addView(Button(this).apply { text = "返回"; setOnClickListener { if (web.canGoBack()) web.goBack() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(detectedBtn, LinearLayout.LayoutParams(0, -2, 2f))
        bottom.addView(Button(this).apply {
            text = "下載清單"
            setOnClickListener { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
        }, LinearLayout.LayoutParams(0, -2, 1f))

        root.addView(top); root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bottom)
        setContentView(root)
        refresh()
        load("https://www.google.com")
    }

    private fun refresh() { detectedBtn.text = "⬇ 偵測到 ${found.size} 個媒體" }

    private fun load(input: String) {
        val t = input.trim()
        val url = when {
            t.contains("://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
        web.loadUrl(url)
    }

    private fun showDetected() {
        if (found.isEmpty()) { Toast.makeText(this, "尚未偵測到媒體，請先播放影片", Toast.LENGTH_SHORT).show(); return }
        val urls = found.toList()
        val names = urls.map { URLUtil.guessFileName(it, null, null) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("選擇要下載的檔案")
            .setItems(names) { _, i -> download(urls[i]) }.show()
    }

    private fun download(url: String) {
        val name = URLUtil.guessFileName(url, null, null)
        val req = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            .addRequestHeader("User-Agent", web.settings.userAgentString)
            .addRequestHeader("Referer", web.url ?: url)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(this, "開始下載：$name", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("") override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
