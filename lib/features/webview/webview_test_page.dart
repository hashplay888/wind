import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hiddify/features/webview/webview_page.dart';
import 'package:hiddify/core/http_client/http_client_provider.dart';

class WebViewTestPage extends ConsumerWidget {
  const WebViewTestPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final httpClient = ref.watch(httpClientProvider);
    final proxyPort = httpClient.port;
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('WebView 代理测试'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '代理配置信息',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text('代理端口: ${proxyPort > 0 ? proxyPort : "未启用"}'),
                    Text('代理状态: ${proxyPort > 0 ? "已启用" : "未启用"}'),
                    const SizedBox(height: 8),
                    Text(
                      '说明: WebView将通过代理端口 $proxyPort 路由网络请求',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              '测试网站',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Expanded(
              child: ListView(
                children: [
                  _buildTestButton(
                    context,
                    '测试 IP 检查',
                    'https://httpbin.org/ip',
                    '检查当前 IP 地址',
                  ),
                  _buildTestButton(
                    context,
                    '测试 User-Agent',
                    'https://httpbin.org/user-agent',
                    '检查 WebView User-Agent',
                  ),
                  _buildTestButton(
                    context,
                    '测试 Headers',
                    'https://httpbin.org/headers',
                    '检查请求头信息',
                  ),
                  _buildTestButton(
                    context,
                    '测试 Google',
                    'https://www.google.com',
                    '测试访问 Google',
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTestButton(
    BuildContext context,
    String title,
    String url,
    String description,
  ) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        title: Text(title),
        subtitle: Text(description),
        trailing: const Icon(Icons.arrow_forward_ios),
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (context) => WebViewPage(
                url: url,
                title: title,
              ),
            ),
          );
        },
      ),
    );
  }
}