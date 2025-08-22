package com.hiddify.hiddify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Proxy
import android.os.Build
import android.os.Parcelable
import android.util.ArrayMap
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class WebViewProxyHandler : MethodCallHandler {
    companion object {
        private const val TAG = "WebViewProxyHandler"
        private const val CHANNEL = "com.hiddify.app/webview_proxy"
        private var proxyHost: String? = null
        private var proxyPort: Int = 0
        private var httpClient: OkHttpClient? = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "setProxy" -> {
                val host = call.argument<String>("host")
                val port = call.argument<Int>("port")
                
                if (host != null && port != null) {
                    try {
                        setWebViewProxy(host, port)
                        setupNetworkInterception(host, port)
                        setGlobalWebViewProxy(host, port)
                        result.success(true)
                        Log.d(TAG, "WebView proxy set to $host:$port")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set WebView proxy", e)
                        result.error("PROXY_ERROR", "Failed to set WebView proxy: ${e.message}", null)
                    }
                } else {
                    result.error("INVALID_ARGUMENTS", "Host and port are required", null)
                }
            }
            "createProxyWebViewClient" -> {
                try {
                    result.success(true)
                    Log.d(TAG, "Proxy WebViewClient created")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create proxy WebViewClient", e)
                    result.error("CLIENT_ERROR", "Failed to create proxy WebViewClient: ${e.message}", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun setupNetworkInterception(host: String, port: Int) {
        try {
            proxyHost = host
            proxyPort = port
            setupHttpClient(host, port)
            Log.d(TAG, "Network interception setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup network interception", e)
            throw e
        }
    }

    private fun setGlobalWebViewProxy(host: String, port: Int) {
        try {
            // 保存代理配置以便恢复
            proxyHost = host
            proxyPort = port
            
            // 强制设置系统级代理属性（确保WebView进程继承）
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port.toString())
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port.toString())
            System.setProperty("http.nonProxyHosts", "")
            System.setProperty("https.nonProxyHosts", "")
            
            // 强制设置Java网络代理
            System.setProperty("java.net.useSystemProxies", "true")
            
            // 设置WebView特定的代理属性
            System.setProperty("webview.proxy.host", host)
            System.setProperty("webview.proxy.port", port.toString())
            System.setProperty("chromium.proxy.host", host)
            System.setProperty("chromium.proxy.port", port.toString())
            
            // 设置默认代理选择器（全局生效）
            try {
                val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(host, port))
                val customProxySelector = object : java.net.ProxySelector() {
                    override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> {
                        Log.d(TAG, "ProxySelector selecting proxy for: $uri")
                        return mutableListOf(proxy)
                    }
                    
                    override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                        Log.w(TAG, "Proxy connection failed for $uri: ${ioe?.message}")
                    }
                }
                
                java.net.ProxySelector.setDefault(customProxySelector)
                Log.d(TAG, "Custom ProxySelector configured")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set ProxySelector", e)
            }
            
            // Android WebView特定代理设置
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                try {
                    val proxyInfoClass = Class.forName("android.net.ProxyInfo")
                    val buildDirectProxyMethod = proxyInfoClass.getMethod("buildDirectProxy", String::class.java, Int::class.java)
                    val proxyInfo = buildDirectProxyMethod.invoke(null, host, port)
                    
                    val webViewClass = Class.forName("android.webkit.WebView")
                    val setWebViewProxyMethod = webViewClass.getDeclaredMethod("setWebViewProxy", android.content.Context::class.java, proxyInfoClass)
                    setWebViewProxyMethod.isAccessible = true
                    
                    val applicationClass = Class.forName("android.app.Application")
                    val currentApplicationMethod = applicationClass.getMethod("getCurrentApplication")
                    val application = currentApplicationMethod.invoke(null)
                    
                    setWebViewProxyMethod.invoke(null, application, proxyInfo)
                    Log.d(TAG, "WebView proxy set via ProxyInfo")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set WebView proxy via ProxyInfo", e)
                }
            }
            
            // OkHttp和其他网络库代理设置
            System.setProperty("okhttp.proxy.host", host)
            System.setProperty("okhttp.proxy.port", port.toString())
            System.setProperty("cronet.proxy.host", host)
            System.setProperty("cronet.proxy.port", port.toString())
            
            Log.d(TAG, "Enhanced global WebView proxy configured: $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set enhanced global WebView proxy", e)
        }
    }
    
    // 添加代理配置恢复方法
    fun restoreProxyConfiguration() {
        if (proxyHost != null && proxyPort > 0) {
            Log.d(TAG, "Restoring proxy configuration: $proxyHost:$proxyPort")
            setGlobalWebViewProxy(proxyHost!!, proxyPort)
        }
    }

    private fun setupHttpClient(host: String, port: Int) {
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(host, port))
        httpClient = OkHttpClient.Builder()
            .proxy(proxy)
            .build()
    }

    fun createProxyWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    interceptRequest(request)
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    interceptRequestLegacy(url ?: "")
                } else {
                    null
                }
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 强制重新应用代理设置
                if (proxyHost != null && proxyPort > 0) {
                    setGlobalWebViewProxy(proxyHost!!, proxyPort)
                }
            }
        }
    }

    private fun interceptRequest(request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        
        // 强制为所有请求创建代理客户端
        if (httpClient == null && proxyHost != null && proxyPort > 0) {
            setupHttpClient(proxyHost!!, proxyPort)
        }
        
        if (httpClient == null) {
            Log.w(TAG, "No proxy client available, request will bypass proxy")
            return null
        }

        try {
            val url = request.url.toString()
            Log.d(TAG, "Intercepting request through proxy: $url")
            
            val requestBuilder = Request.Builder().url(url)
            
            request.requestHeaders.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val response: Response = httpClient!!.newCall(requestBuilder.build()).execute()
            val contentType = response.header("Content-Type") ?: "text/html"
            val encoding = getEncodingFromContentType(contentType)
            
            Log.d(TAG, "Request successfully proxied: $url")
            
            return WebResourceResponse(
                contentType.split(";")[0].trim(),
                encoding,
                response.code,
                response.message,
                response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                response.body?.byteStream()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to intercept request through proxy: ${request.url}", e)
            return null
        }
    }

    private fun interceptRequestLegacy(url: String): WebResourceResponse? {
        // 强制为所有请求创建代理客户端
        if (httpClient == null && proxyHost != null && proxyPort > 0) {
            setupHttpClient(proxyHost!!, proxyPort)
        }
        
        if (httpClient == null) {
            Log.w(TAG, "No proxy client available for legacy request, will bypass proxy")
            return null
        }

        try {
            Log.d(TAG, "Intercepting legacy request through proxy: $url")
            
            val request = Request.Builder().url(url).build()
            val response: Response = httpClient!!.newCall(request).execute()
            val contentType = response.header("Content-Type") ?: "text/html"
            val encoding = getEncodingFromContentType(contentType)
            
            Log.d(TAG, "Legacy request successfully proxied: $url")
            
            return WebResourceResponse(
                contentType.split(";")[0].trim(),
                encoding,
                ByteArrayInputStream(response.body?.bytes() ?: ByteArray(0))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to intercept legacy request through proxy: $url", e)
            return null
        }
    }

    private fun getEncodingFromContentType(contentType: String): String {
        val charsetRegex = Regex("charset=([^;\\s]+)")
        val matchResult = charsetRegex.find(contentType)
        return matchResult?.groupValues?.get(1) ?: "utf-8"
    }

    @SuppressLint("PrivateApi")
    private fun setWebViewProxy(host: String, port: Int) {
        try {
            setSystemProxyProperties(host, port)
            Log.d(TAG, "WebView proxy configuration completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set WebView proxy", e)
            throw e
        }
    }

    private fun setSystemProxyProperties(host: String, port: Int) {
        try {
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port.toString())
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port.toString())
            System.setProperty("http.nonProxyHosts", "")
            System.setProperty("https.nonProxyHosts", "")
            
            Log.d(TAG, "System proxy properties set to $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system proxy properties", e)
            throw e
        }
    }
}