package com.adpluga.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.MainThread
import com.adpluga.config.Constants
import com.adpluga.logger.AdPlugaLogger

public class HtmlAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView = WebView(context)
    private var initialLoaded: Boolean = false
    private var destroyed: Boolean = false

    public var onClick: (() -> Unit)? = null

    init {
        setBackgroundColor(0x00000000)
        webView.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.setBackgroundColor(0x00000000)
        addView(webView)
        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "$userAgentString AdPluga/${Constants.SDK_VERSION}"
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!initialLoaded) {
                    initialLoaded = true
                    return false
                }
                val url = request.url
                if (!isAllowedScheme(url)) return true
                onClick?.invoke()
                openExternal(url)
                return true
            }
        }
    }

    private fun isAllowedScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { AdPlugaLogger.warn("html click open failed url=$uri", it) }
    }

    @MainThread
    public fun load(html: String? = null, assetUrl: String? = null, baseUrl: String? = null) {
        if (destroyed) return
        initialLoaded = false
        if (!html.isNullOrEmpty()) {
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        } else if (!assetUrl.isNullOrEmpty()) {
            val parsed = runCatching { Uri.parse(assetUrl) }.getOrNull()
            if (parsed != null && isAllowedScheme(parsed)) {
                webView.loadUrl(assetUrl)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    public fun destroy() {
        if (destroyed) return
        destroyed = true
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.loadUrl("about:blank")
        removeView(webView)
        webView.destroy()
    }
}
