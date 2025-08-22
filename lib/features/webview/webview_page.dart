import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'package:hiddify/core/http_client/http_client_provider.dart';
import 'package:hiddify/features/common/nested_app_bar.dart';
import 'package:hiddify/features/config_option/data/config_option_repository.dart';

class WebViewPage extends ConsumerStatefulWidget {
  final String url;
  final String? title;

  const WebViewPage({
    super.key,
    required this.url,
    this.title,
  });

  @override
  ConsumerState<WebViewPage> createState() => _WebViewPageState();
}

class _WebViewPageState extends ConsumerState<WebViewPage> {
  late final WebViewController _controller;
  bool _isLoading = true;
  String? _currentTitle;
  final bool _isWindows = Platform.isWindows;

  @override
  void initState() {
    super.initState();
    if (!_isWindows) {
      // 在WebView创建前预先配置代理
      _preConfigureProxy();
    }
  }
    
  @override
  void dispose() {
    _stopProxyMonitoring();
    super.dispose();
    }
  
  Future<void> _preConfigureProxy() async {
    // 确保在WebView创建前设置代理
    await _configureProxy();
    // 等待一小段时间确保代理设置生效
    await Future.delayed(const Duration(milliseconds: 500    ));
    
    // 启动代理状态监控
    _startProxyMonitoring();
    
    // 然后初始化WebView
    _initializeWebView();
  }
  
  Timer? _proxyMonitorTimer;
  int _lastKnownPort = 0;
  
  void _startProxyMonitoring() {
    _proxyMonitorTimer?.cancel();
    _proxyMonitorTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      try {
        // 检查VPN连接状态
        final isVpnConnected = await _checkVpnStatus();
        if (!isVpnConnected) {
          debugPrint('VPN未连接，暂停代理监控');
          return;
        }
        
        final currentPort = await _getVpnProxyPort();
        if (currentPort != _lastKnownPort) {
          debugPrint('检测到代理端口变化: $_lastKnownPort -> $currentPort');
          _lastKnownPort = currentPort;
          await _configureProxy();
        }
      } catch (e) {
        debugPrint('代理监控检查失败: $e');
      }
    });
  }
  
  Future<bool> _checkVpnStatus() async {
    try {
      const platform = MethodChannel('com.hiddify.app/vpn');
      final isConnected = await platform.invokeMethod('checkVpnStatus');
      return isConnected ?? false;
    } catch (e) {
      debugPrint('检查VPN状态失败: $e');
        return false;
    }
  }
  
  void _stopProxyMonitoring() {
    _proxyMonitorTimer?.cancel();
    _proxyMonitorTimer = null;
  }

  void _initializeWebView() {
    final proxyPort = ref.read(ConfigOptions.mixedPort);
    final serviceMode = ref.read(ConfigOptions.serviceMode);

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (int progress) {
            // 更新加载进度
          },
          onPageStarted: (String url) {
            setState(() {
              _isLoading = true;
            });
            debugPrint('WebView page started: $url');
            // 强制重新应用代理设置
            _configureProxy();
          },
          onPageFinished: (String url) {
            setState(() {
              _isLoading = false;
            });
            _updateTitle();
            _verifyProxyConfiguration();
            debugPrint('WebView page finished: $url');
              },
        ),
      );
    
    _configureProxy();
    _controller.loadRequest(Uri.parse(widget.url));
  }







  Future<void> _updateTitle() async {
    final title = await _controller.getTitle();
    if (mounted) {
      setState(() {
        _currentTitle = title;
      });
    }
  }

  Future<void> _configureProxy() async {
    try {
      // 获取VPN服务实际使用的代理端口
      final proxyPort = await _getVpnProxyPort();
      
      // 方法1: 设置全局WebView代理
      const platform = MethodChannel('com.hiddify.app/vpn');
      final result = await platform.invokeMethod('setWebViewProxy', {
        'host': '127.0.0.1',
        'port': proxyPort,
      });
      
      // 方法2: 设置自定义WebViewClient进行网络拦截
      const webviewPlatform = MethodChannel('com.hiddify.app/webview_proxy');
      await webviewPlatform.invokeMethod('setProxy', {
        'host': '127.0.0.1',
        'port': proxyPort,
      });
      
      // 方法3: 创建代理WebViewClient
      await webviewPlatform.invokeMethod('createProxyWebViewClient');
      
      if (kDebugMode) {
        debugPrint('WebView proxy configuration completed: $result');
        debugPrint('Proxy port: $proxyPort');
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Failed to configure WebView proxy: $e');
      }
    }
  }

  Future<int> _getVpnProxyPort() async {
    try {
      // 通过MethodChannel获取VPN服务的实际代理端口
      const platform = MethodChannel('com.hiddify.app/vpn');
      final port = await platform.invokeMethod('getProxyPort');
      return port ?? ref.read(ConfigOptions.mixedPort);
    } catch (e) {
      debugPrint('获取VPN代理端口失败，使用默认端口: $e');
      return ref.read(ConfigOptions.mixedPort);
    }
  }

  Future<void> _setupNetworkInterception() async {
    try {
      const platform = MethodChannel('com.hiddify.app/webview_proxy');
      
      final result = await platform.invokeMethod('createProxyWebViewClient');
      
      if (kDebugMode) {
        debugPrint('Proxy WebViewClient setup result: $result');
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Failed to setup proxy WebViewClient: $e');
      }
    }
  }

  Future<void> _verifyProxyConfiguration() async {
    try {
      await _controller.runJavaScript('''
        console.log('=== Network Test Debug Info ===');
        console.log('User Agent:', navigator.userAgent);
        console.log('Location:', window.location.href);
        
        if (typeof navigator.connection !== 'undefined') {
          console.log('Connection type:', navigator.connection.effectiveType);
        }
        
        fetch('https://httpbin.org/ip')
          .then(response => response.json())
          .then(data => {
            console.log('Network Test Result:', data);
            const resultDiv = document.createElement('div');
            resultDiv.style.cssText = `
              position: fixed;
              top: 10px;
              right: 10px;
              background: #4CAF50;
              color: white;
              padding: 10px;
              border-radius: 5px;
              z-index: 10000;
              font-family: Arial, sans-serif;
              font-size: 12px;
              max-width: 200px;
            `;
            resultDiv.innerHTML = `✓ Network Test OK<br>IP: \${data.origin}`;
            document.body.appendChild(resultDiv);
            setTimeout(() => resultDiv.remove(), 5000);
          })
          .catch(error => {
            console.error('Network Test Failed:', error);
            const resultDiv = document.createElement('div');
            resultDiv.style.cssText = `
              position: fixed;
              top: 10px;
              right: 10px;
              background: #f44336;
              color: white;
              padding: 10px;
              border-radius: 5px;
              z-index: 10000;
              font-family: Arial, sans-serif;
              font-size: 12px;
              max-width: 200px;
            `;
            resultDiv.innerHTML = `✗ Network Test Failed<br>\${error.message}`;
            document.body.appendChild(resultDiv);
            setTimeout(() => resultDiv.remove(), 5000);
          });
      ''');
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Failed to verify proxy configuration: $e');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          NestedAppBar(
            title: Text(_currentTitle ?? widget.title ?? 'WebView'),
            actions: [
              if (!_isWindows)
                IconButton(
                  icon: const Icon(Icons.refresh),
                  onPressed: () => _controller.reload(),
                ),
              if (_isWindows)
                IconButton(
                  icon: const Icon(Icons.open_in_browser),
                  onPressed: () => _openInExternalBrowser(),
                ),
                IconButton(
                   icon: const Icon(Icons.arrow_back),
                   onPressed: () async {
                     if (await _controller.canGoBack()) {
                       _controller.goBack();
                     } else {
                       Navigator.of(context).pop();
                     }
                   },
                ),
              ],
            ),
          SliverFillRemaining(
            child: _isWindows ? _buildWindowsView() : _buildWebView(),
          ),
        ],
      ),
    );
  }

  Widget _buildWebView() {
    return Stack(
      children: [
        WebViewWidget(controller: _controller),
        if (_isLoading)
          const Center(
            child: CircularProgressIndicator(),
          ),
      ],
    );
  }

  Widget _buildWindowsView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.web,
              size: 64,
              color: Colors.grey,
            ),
            const SizedBox(height: 16),
            const Text(
              'WebView 在 Windows 平台上不可用',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              '请点击下方按钮在外部浏览器中打开链接',
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey[600],
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _openInExternalBrowser,
              icon: const Icon(Icons.open_in_browser),
              label: const Text('在浏览器中打开'),
            ),
            const SizedBox(height: 16),
            SelectableText(
              widget.url,
              style: TextStyle(
                fontSize: 12,
                color: Colors.grey[500],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openInExternalBrowser() async {
    final uri = Uri.parse(widget.url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('无法打开链接: ${widget.url}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }
}
