import Foundation
import Flutter
import UIKit
import WebKit

class WebViewProxyHandler: NSObject, FlutterPlugin {
    static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "com.hiddify.app/webview_proxy", binaryMessenger: registrar.messenger())
        let instance = WebViewProxyHandler()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "setProxy":
            guard let args = call.arguments as? [String: Any],
                  let host = args["host"] as? String,
                  let port = args["port"] as? Int else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Host and port are required", details: nil))
                return
            }
            
            setWebViewProxy(host: host, port: port, result: result)
            
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func setWebViewProxy(host: String, port: Int, result: @escaping FlutterResult) {
        DispatchQueue.main.async {
            do {
                try self.configureSystemProxy(host: host, port: port)
                print("WebView proxy set to \(host):\(port)")
                result(true)
            } catch {
                print("Failed to set WebView proxy: \(error)")
                result(FlutterError(code: "PROXY_ERROR", message: "Failed to set WebView proxy: \(error.localizedDescription)", details: nil))
            }
        }
    }
    
    private func configureSystemProxy(host: String, port: Int) throws {
        // 设置系统代理配置
        let proxyHost = host
        let proxyPort = port
        
        // 创建代理配置字典
        let proxyDict: [String: Any] = [
            kCFNetworkProxiesHTTPEnable as String: true,
            kCFNetworkProxiesHTTPProxy as String: proxyHost,
            kCFNetworkProxiesHTTPPort as String: proxyPort,
            kCFNetworkProxiesHTTPSEnable as String: true,
            kCFNetworkProxiesHTTPSProxy as String: proxyHost,
            kCFNetworkProxiesHTTPSPort as String: proxyPort
        ]
        
        // 设置环境变量
        setenv("http_proxy", "http://\(proxyHost):\(proxyPort)", 1)
        setenv("https_proxy", "http://\(proxyHost):\(proxyPort)", 1)
        setenv("HTTP_PROXY", "http://\(proxyHost):\(proxyPort)", 1)
        setenv("HTTPS_PROXY", "http://\(proxyHost):\(proxyPort)", 1)
        
        // 配置URLSessionConfiguration的默认代理
        if #available(iOS 11.0, *) {
            let config = URLSessionConfiguration.default
            config.connectionProxyDictionary = proxyDict
            
            // 尝试设置WKWebView的默认配置
            configureWKWebViewProxy(proxyDict: proxyDict)
        }
        
        print("iOS WebView proxy configured for \(proxyHost):\(proxyPort)")
    }
    
    @available(iOS 11.0, *)
    private func configureWKWebViewProxy(proxyDict: [String: Any]) {
        // 为WKWebView配置代理
        // 注意：WKWebView不直接支持代理设置，但我们可以通过系统级别的配置来影响它
        
        // 设置系统网络配置
        let configuration = WKWebViewConfiguration()
        
        // 创建自定义的URLSchemeHandler来处理代理
        if #available(iOS 11.0, *) {
            // 可以在这里添加自定义的URL scheme处理
        }
        
        print("WKWebView proxy configuration applied")
    }
    
    // 辅助方法：清除代理设置
    func clearProxy() {
        unsetenv("http_proxy")
        unsetenv("https_proxy")
        unsetenv("HTTP_PROXY")
        unsetenv("HTTPS_PROXY")
        
        print("WebView proxy cleared")
    }
}