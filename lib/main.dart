import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/bootstrap.dart';
// 导入环境配置文件
import 'package:hiddify/core/model/environment.dart';
import 'dart:io' show Platform;

// 应用程序的主入口函数
void main() async {
  // 确保Flutter的Widgets绑定已初始化
  final widgetsBinding = WidgetsFlutterBinding.ensureInitialized();

  // 注意：Windows平台的WebView将使用webview_windows包
  // 不需要在这里初始化WebViewPlatform，因为webview_flutter在Windows上不受支持

  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
    ),
  );

  return lazyBootstrap(widgetsBinding, Environment.dev);
}
