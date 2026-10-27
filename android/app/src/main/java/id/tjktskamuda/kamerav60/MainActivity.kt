package id.tjktskamuda.kamerav60

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Kamera V60 versi APK: aplikasi web dijalankan di WebView dari file di dalam APK (offline),
 * dengan jembatan "KameraAndroid" untuk menyimpan foto/video langsung ke galeri.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pending: PermissionRequest? = null

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val req = pending ?: return@registerForActivityResult
            pending = null
            grantAllowed(req)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // Jaga tampilan aplikasi tidak tertutup lubang kamera depan / bar sistem
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsetsCompat.CONSUMED
        }
        hideSystemBars()

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        web.addJavascriptInterface(Bridge(this), "KameraAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) { }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handlePermission(request) }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__onBack && window.__onBack()) ? 'y' : 'n'") { r ->
                    if (r == null || !r.contains("y")) finish()
                }
            }
        })

        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun handlePermission(req: PermissionRequest) {
        val needed = mutableListOf<String>()
        if (req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) needed.add(Manifest.permission.CAMERA)
        if (req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) needed.add(Manifest.permission.RECORD_AUDIO)
        val missing = needed.filter { !has(it) }
        if (missing.isEmpty()) {
            grantAllowed(req)
            return
        }
        pending?.deny()
        pending = req
        askPermissions.launch(missing.toTypedArray())
    }

    private fun grantAllowed(req: PermissionRequest) {
        val ok = req.resources.filter {
            (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && has(Manifest.permission.CAMERA)) ||
                (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && has(Manifest.permission.RECORD_AUDIO))
        }
        if (ok.isEmpty()) req.deny() else req.grant(ok.toTypedArray())
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}

/** Menyajikan file aplikasi dari folder assets APK dengan tipe file yang benar (bekerja tanpa internet). */
class AssetHandler(private val ctx: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        return try {
            val mime = when (path.substringAfterLast('.', "").lowercase()) {
                "html", "htm" -> "text/html"
                "js", "mjs" -> "text/javascript"
                "wasm" -> "application/wasm"
                "json", "webmanifest" -> "application/json"
                "css" -> "text/css"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "svg" -> "image/svg+xml"
                else -> "application/octet-stream"
            }
            val res = WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, ctx.assets.open(path))
            res.responseHeaders = mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*")
            res
        } catch (e: IOException) {
            null
        }
    }
}

/** Jembatan JavaScript → Android: simpan ke galeri (MediaStore), bagikan, kunci orientasi. */
class Bridge(private val act: MainActivity) {

    private val open = ConcurrentHashMap<String, Pair<Uri, OutputStream>>()

    @JavascriptInterface
    fun begin(name: String, mime: String, isVideo: Boolean): String {
        return try {
            val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Kamera V60")
                put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = act.contentResolver.insert(collection, values) ?: return ""
            val os = act.contentResolver.openOutputStream(uri) ?: return ""
            val id = UUID.randomUUID().toString()
            open[id] = Pair(uri, BufferedOutputStream(os, 1 shl 20))
            id
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun write(id: String, b64: String): Boolean {
        val f = open[id] ?: return false
        return try {
            f.second.write(Base64.decode(b64, Base64.DEFAULT))
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun finish(id: String): String {
        val f = open.remove(id) ?: return ""
        return try {
            f.second.close()
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            act.contentResolver.update(f.first, values, null, null)
            f.first.toString()
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun abort(id: String) {
        val f = open.remove(id) ?: return
        try { f.second.close() } catch (_: Exception) { }
        try { act.contentResolver.delete(f.first, null, null) } catch (_: Exception) { }
    }

    @JavascriptInterface
    fun remove(uri: String): Boolean {
        return try {
            act.contentResolver.delete(Uri.parse(uri), null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun share(uri: String, mime: String) {
        act.runOnUiThread {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                act.startActivity(Intent.createChooser(send, "Bagikan"))
            } catch (_: Exception) { }
        }
    }

    @JavascriptInterface
    fun lockOrientation(lock: Boolean) {
        act.runOnUiThread {
            act.requestedOrientation = if (lock) ActivityInfo.SCREEN_ORIENTATION_LOCKED
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @JavascriptInterface
    fun version(): String = BuildConfig.VERSION_NAME
}
