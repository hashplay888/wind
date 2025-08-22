import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:hiddify/core/router/routes.dart';

class WebViewService {
  static Future<void> openUrl(
    BuildContext context, {
    required String url,
    String? title,
  }) async {
    WebViewRoute(url: url, title: title).go(context);
  }
}