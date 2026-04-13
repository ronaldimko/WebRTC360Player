package com.example.webrtc360player

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var gyroManager: GyroManager? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create WebView and set as content view first
        val wv = WebView(this)
        webView = wv
        setContentView(wv)

        // Configure settings
        wv.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            allowFileAccess                  = true
            allowContentAccess               = true
            mediaPlaybackRequiresUserGesture = false
        }

        wv.webViewClient = WebViewClient()

        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                android.util.Log.d("WebView", msg.message())
                return true
            }
        }

        // Setup gyroscope
        gyroManager = GyroManager(this) { deltaYaw, deltaPitch ->
            runOnUiThread {
                webView?.evaluateJavascript(
                    "if(typeof rotateCamera==='function'){rotateCamera($deltaYaw,$deltaPitch);}",
                    null
                )
            }
        }

        // Request permissions then load
        if (hasAllPermissions()) {
            loadPage()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun loadPage() {
        webView?.loadUrl("file:///android_asset/index.html")
    }

    private fun hasAllPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadPage()
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        gyroManager?.start()
    }

    override fun onPause() {
        super.onPause()
        gyroManager?.stop()
        webView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroManager?.stop()
        webView?.destroy()
        webView = null
    }
}
