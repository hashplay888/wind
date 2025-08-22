import 'package:flutter/material.dart';
import 'package:hiddify/features/webview/webview_service.dart';

/// WebView功能使用示例
class WebViewExample extends StatelessWidget {
  const WebViewExample({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('WebView 示例'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '点击下面的按钮来测试应用内URL加载功能：',
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () {
                WebViewService.openUrl(
                  context,
                  url: 'https://www.google.com',
                  title: 'Google',
                );
              },
              child: const Text('打开 Google'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: () {
                WebViewService.openUrl(
                  context,
                  url: 'https://github.com',
                  title: 'GitHub',
                );
              },
              child: const Text('打开 GitHub'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: () {
                WebViewService.goToWebView(
                  context,
                  url: 'https://flutter.dev',
                  title: 'Flutter 官网',
                );
              },
              child: const Text('使用路由打开 Flutter 官网'),
            ),
            const SizedBox(height: 30),
            const Text(
              '使用方法：',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 10),
            const Text(
              '1. 导入 WebViewService:\n'
              '   import \'package:hiddify/features/webview/webview.dart\';\n\n'
              '2. 使用 openUrl 方法打开URL：\n'
              '   WebViewService.openUrl(context, url: \'https://example.com\');\n\n'
              '3. 使用 goToWebView 方法通过路由打开：\n'
              '   WebViewService.goToWebView(context, url: \'https://example.com\');',
              style: TextStyle(fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}