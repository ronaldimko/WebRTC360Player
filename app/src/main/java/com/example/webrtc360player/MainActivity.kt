package com.example.webrtc360player

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true

        webView.loadUrl("file:///android_asset/viewer.html")
    }
}