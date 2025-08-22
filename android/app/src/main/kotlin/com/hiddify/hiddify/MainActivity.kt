package com.hiddify.hiddify

import android.annotation.SuppressLint
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import android.webkit.WebView
import android.webkit.WebSettings
import java.net.InetSocketAddress
import java.net.Proxy
import org.json.JSONObject
import com.hiddify.hiddify.bg.ServiceConnection
import com.hiddify.hiddify.bg.ServiceNotification
import com.hiddify.hiddify.constant.Alert
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.Status
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList


class MainActivity : FlutterFragmentActivity(), ServiceConnection.Callback {
    companion object {
        private const val TAG = "ANDROID/MyActivity"
        lateinit var instance: MainActivity

        const val VPN_PERMISSION_REQUEST_CODE = 1001
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1010
    }

    private val connection = ServiceConnection(this, this)

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)
    val serviceAlerts = MutableLiveData<ServiceEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        
        // 应用启动时恢复WebView代理配置
        restoreWebViewProxyOnStartup()
    }
    
    private fun restoreWebViewProxyOnStartup() {
        lifecycleScope.launch {
            try {
                // 等待服务连接建立
                kotlinx.coroutines.delay(1000)
                
                // 检查VPN是否已连接
                if (isVpnConnected()) {
                    val port = getVpnProxyPort()
                    if (port > 0) {
                        setWebViewGlobalProxy("127.0.0.1", port)
                        Log.d(TAG, "WebView proxy restored on startup: 127.0.0.1:$port")
                    } else {
                        Log.w(TAG, "VPN proxy port is invalid: $port")
                    }
                } else {
                    Log.w(TAG, "VPN is not connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore WebView proxy on startup", e)
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        instance = this
        reconnect()
        flutterEngine.plugins.add(MethodHandler(lifecycleScope))
        flutterEngine.plugins.add(PlatformSettingsHandler())
        flutterEngine.plugins.add(EventHandler())
        flutterEngine.plugins.add(LogHandler())
        flutterEngine.plugins.add(GroupsChannel(lifecycleScope))
        flutterEngine.plugins.add(ActiveGroupsChannel(lifecycleScope))
        flutterEngine.plugins.add(StatsChannel(lifecycleScope))
        
        // 注册WebView代理处理器
        val webViewProxyHandler = WebViewProxyHandler()
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.hiddify.app/webview_proxy")
            .setMethodCallHandler(webViewProxyHandler)
            
        // 注册VPN代理端口获取处理器
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.hiddify.app/vpn")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getProxyPort" -> {
                        try {
                            val proxyPort = getVpnProxyPort()
                            result.success(proxyPort)
                        } catch (e: Exception) {
                            result.error("ERROR", "获取VPN代理端口失败: ${e.message}", null)
                        }
                    }
                    "setWebViewProxy" -> {
                        val host = call.argument<String>("host")
                        val port = call.argument<Int>("port")
                        
                        if (host != null && port != null) {
                            try {
                                setWebViewGlobalProxy(host, port)
                                result.success(true)
                                Log.d("MainActivity", "WebView global proxy set to $host:$port")
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to set WebView global proxy", e)
                                result.error("WEBVIEW_PROXY_ERROR", "Failed to set WebView global proxy: ${e.message}", null)
                            }
                        } else {
                            result.error("INVALID_ARGUMENTS", "Host and port are required", null)
                        }
                    }
                    "checkVpnStatus" -> {
                        val isConnected = isVpnConnected()
                        result.success(isConnected)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    fun reconnect() {
        connection.connect()
    }
    
    private fun getVpnProxyPort(): Int {
        return try {
            // 从Settings获取配置的代理端口
            val configOptions = Settings.configOptions
            if (configOptions.isNotBlank()) {
                // 解析配置中的混合端口
                val mixedPortRegex = """"mixed-port"\s*:\s*(\d+)""".toRegex()
                val matchResult = mixedPortRegex.find(configOptions)
                if (matchResult != null) {
                    matchResult.groupValues[1].toInt()
                } else {
                    12334 // 默认端口
                }
            } else {
                12334 // 默认端口
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取VPN代理端口失败: ${e.message}")
            12334 // 默认端口
        }
    }
    
    private fun isVpnConnected(): Boolean {
        return try {
            serviceStatus.value == Status.Started
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check VPN status", e)
            false
        }
    }
    
    private fun setWebViewGlobalProxy(host: String, port: Int) {
        try {
            // 设置系统代理属性
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port.toString())
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port.toString())
            System.setProperty("http.nonProxyHosts", "")
            System.setProperty("https.nonProxyHosts", "")
            
            // 设置Java网络代理
            System.setProperty("java.net.useSystemProxies", "true")
            
            // 强制设置默认代理选择器
            try {
                val proxyClass = Class.forName("java.net.Proxy")
                val proxyTypeClass = Class.forName("java.net.Proxy\$Type")
                val httpType = proxyTypeClass.getField("HTTP").get(null)
                val socketAddressClass = Class.forName("java.net.InetSocketAddress")
                val socketAddress = socketAddressClass.getConstructor(String::class.java, Int::class.java).newInstance(host, port)
                val proxy = proxyClass.getConstructor(proxyTypeClass, java.net.SocketAddress::class.java).newInstance(httpType, socketAddress)
                
                val proxySelectorClass = Class.forName("java.net.ProxySelector")
                val setDefaultMethod = proxySelectorClass.getMethod("setDefault", proxySelectorClass)
                
                // 创建自定义ProxySelector
                val customProxySelector = object : java.net.ProxySelector() {
                    override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> {
                        return mutableListOf(proxy as java.net.Proxy)
                    }
                    
                    override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                        // 连接失败处理
                    }
                }
                
                setDefaultMethod.invoke(null, customProxySelector)
                Log.d(TAG, "Custom ProxySelector set")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set custom ProxySelector", e)
            }
            
            // Android WebView特定设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    val proxyInfoClass = Class.forName("android.net.ProxyInfo")
                    val buildDirectProxyMethod = proxyInfoClass.getMethod("buildDirectProxy", String::class.java, Int::class.java)
                    val proxyInfo = buildDirectProxyMethod.invoke(null, host, port)
                    
                    val webViewClass = Class.forName("android.webkit.WebView")
                    val setWebViewProxyMethod = webViewClass.getDeclaredMethod("setWebViewProxy", android.content.Context::class.java, proxyInfoClass)
                    setWebViewProxyMethod.isAccessible = true
                    setWebViewProxyMethod.invoke(null, this, proxyInfo)
                    Log.d(TAG, "WebView proxy set via ProxyInfo")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set WebView proxy via ProxyInfo", e)
                }
            }
            
            // OkHttp代理设置
            System.setProperty("okhttp.proxy.host", host)
            System.setProperty("okhttp.proxy.port", port.toString())
            
            Log.d(TAG, "Enhanced WebView proxy configured: $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set enhanced WebView proxy", e)
        }
    }

    fun startService() {
        if (!ServiceNotification.checkPermission()) {
            grantNotificationPermission()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                reconnect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                if (prepare()) {
                    Log.d(TAG, "VPN permission required")
                    return@launch
                }
            }

            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(Application.application, intent)
            }
        }
    }

    private suspend fun prepare() = withContext(Dispatchers.Main) {
        try {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                startActivityForResult(intent, VPN_PERMISSION_REQUEST_CODE)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            onServiceAlert(Alert.RequestVPNPermission, e.message)
            false
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        serviceStatus.postValue(status)
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        serviceAlerts.postValue(ServiceEvent(Status.Stopped, type, message))
    }

    override fun onServiceWriteLog(message: String?) {
        if (logList.size > 300) {
            logList.removeFirst()
        }
        logList.addLast(message)
        logCallback?.invoke(false)
    }

    override fun onServiceResetLogs(messages: MutableList<String>) {
        logList.clear()
        logList.addAll(messages)
        logCallback?.invoke(true)
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

    @SuppressLint("NewApi")
    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startService()
            } else onServiceAlert(Alert.RequestNotificationPermission, null)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) startService()
            else onServiceAlert(Alert.RequestVPNPermission, null)
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) startService()
            else onServiceAlert(Alert.RequestNotificationPermission, null)
        }
    }
}
