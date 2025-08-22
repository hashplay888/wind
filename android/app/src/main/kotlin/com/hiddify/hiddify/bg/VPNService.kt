package com.hiddify.hiddify.bg
import android.util.Log

import com.hiddify.hiddify.Settings
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import com.hiddify.hiddify.constant.PerAppProxyMode
import com.hiddify.hiddify.ktx.toIpPrefix
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "A/VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        service.onStartCommand(intent, flags, startId)

    override fun onBind(intent: Intent): IBinder {
        val binder = super.onBind(intent)
        if (binder != null) {
            return binder
        }
        return service.onBind(intent)
    }

    override fun onDestroy() {
        // 清理WebView代理设置
        clearWebViewProxy()
        service.onDestroy()
    }

    override fun onRevoke() {
        runBlocking {
            withContext(Dispatchers.Main) {
                // 清理WebView代理设置
                clearWebViewProxy()
                service.onRevoke()
            }
        }
    }
    
    private fun clearWebViewProxy() {
        try {
            // 清理系统代理属性
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("http.nonProxyHosts")
            System.clearProperty("https.nonProxyHosts")
            System.clearProperty("java.net.useSystemProxies")
            System.clearProperty("okhttp.proxy.host")
            System.clearProperty("okhttp.proxy.port")
            System.clearProperty("cronet.proxy.host")
            System.clearProperty("cronet.proxy.port")
            
            // 重置默认代理选择器
            java.net.ProxySelector.setDefault(null)
            
            Log.d(TAG, "WebView proxy settings cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView proxy settings", e)
        }
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    var systemProxyAvailable = false
    var systemProxyEnabled = false
    fun addIncludePackage(builder: Builder, packageName: String) {
        try {     
            Log.d("VpnService","Including $packageName")
            builder.addAllowedApplication(packageName)
        } catch (e: NameNotFoundException) {
            Log.w("VpnService","Package not found: $packageName")
        }
    }

    fun addExcludePackage(builder: Builder, packageName: String) {
        try {     
            Log.d("VpnService","Excluding $packageName")
            builder.addDisallowedApplication(packageName)
        } catch (e: NameNotFoundException) {
        }
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder()
            .setSession("sing-box")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddress = options.inet6RouteAddress
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                    }
                } else {
                    builder.addRoute("::", 0)
                }

                val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                while (inet4RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                }

                val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                while (inet6RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet6RouteExcludeAddress.next().toIpPrefix())
                }
            } else {
                val inet4RouteAddress = options.inet4RouteRange
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        val address = inet4RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }

                val inet6RouteAddress = options.inet6RouteRange
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        val address = inet6RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }
            }

            if (Settings.perAppProxyEnabled) {
                val appList = Settings.perAppProxyList
                if (Settings.perAppProxyMode == PerAppProxyMode.INCLUDE) {
                    appList.forEach {
                        addIncludePackage(builder,it)
                    }
                    // 确保当前应用包名被包含，以便WebView流量通过VPN
                    addIncludePackage(builder,packageName)
                } else {
                    appList.forEach {
                        addExcludePackage(builder,it)
                    }
                    // 确保当前应用不被排除，以便WebView流量通过VPN
                    // addExcludePackage(builder,packageName)
                }
            } else {
                val includePackage = options.includePackage
                if (includePackage.hasNext()) {
                    while (includePackage.hasNext()) {
                        addIncludePackage(builder,includePackage.next())
                    }
                    // 在包含模式下，确保当前应用也被包含
                    addIncludePackage(builder,packageName)
                } else {
                    // 默认情况下包含当前应用，确保WebView流量通过VPN
                    addIncludePackage(builder,packageName)
                }
                
                val excludePackage = options.excludePackage
                if (excludePackage.hasNext()) {
                    while (excludePackage.hasNext()) {
                        val excludePkg = excludePackage.next()
                        // 不排除当前应用，确保WebView流量通过VPN
                        if (excludePkg != packageName) {
                            addExcludePackage(builder,excludePkg)
                        }
                    }
                }
            }
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemProxyAvailable = true
            systemProxyEnabled = Settings.systemProxyEnabled
            if (systemProxyEnabled) builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(
                    options.httpProxyServer, options.httpProxyServerPort
                )
            )
        } else {
            systemProxyAvailable = false
            systemProxyEnabled = false
        }

        val pfd =
            builder.establish() ?: error("android: the application is not prepared or is revoked")
        service.fileDescriptor = pfd
        return pfd.fd
    }

    override fun writeLog(message: String) = service.writeLog(message)

}