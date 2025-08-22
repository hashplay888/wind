# WebView 功能模块

本模块为 Hiddify 应用添加了应用内 URL 加载功能，支持在应用内直接浏览网页内容。

## 功能特性

- ✅ 应用内网页加载
- ✅ 支持 HTTP/HTTPS 协议
- ✅ 页面加载进度显示
- ✅ 页面刷新功能
- ✅ 动态页面标题
- ✅ 路由集成支持
- ✅ 错误处理

## 依赖项

- `webview_flutter: ^4.4.2` - WebView 核心功能
- `go_router` - 路由管理（项目已有）

## 使用方法

### 1. 导入模块

```dart
import 'package:hiddify/features/webview/webview.dart';
```

### 2. 直接打开 URL

```dart
// 使用 Navigator 打开
WebViewService.openUrl(
  context,
  url: 'https://www.example.com',
  title: '示例网站', // 可选
);
```

### 3. 使用路由打开

```dart
// 使用 GoRouter 导航
WebViewService.goToWebView(
  context,
  url: 'https://www.example.com',
  title: '示例网站', // 可选
);
```

### 4. 直接使用 WebViewPage 组件

```dart
Navigator.of(context).push(
  MaterialPageRoute(
    builder: (context) => WebViewPage(
      url: 'https://www.example.com',
      title: '示例网站',
    ),
  ),
);
```

## 文件结构

```
lib/features/webview/
├── webview.dart           # 模块导出文件
├── webview_page.dart      # WebView 页面组件
├── webview_service.dart   # WebView 服务类
├── webview_example.dart   # 使用示例
└── README.md             # 说明文档
```

## API 参考

### WebViewService

#### openUrl()

在应用内打开指定 URL。

**参数：**
- `context` (BuildContext): 上下文
- `url` (String): 要加载的 URL
- `title` (String?, 可选): 页面标题

**异常：**
- `ArgumentError`: 当 URL 格式无效时抛出

#### goToWebView()

使用 GoRouter 导航到 WebView 页面。

**参数：**
- `context` (BuildContext): 上下文
- `url` (String): 要加载的 URL
- `title` (String?, 可选): 页面标题

### WebViewPage

WebView 页面组件，提供完整的网页浏览功能。

**构造参数：**
- `url` (String): 要加载的 URL
- `title` (String?, 可选): 页面标题

**功能：**
- 页面加载进度显示
- 动态标题更新
- 刷新按钮
- 错误处理

## 注意事项

1. **URL 验证**: 只支持 HTTP 和 HTTPS 协议的 URL
2. **网络权限**: 确保应用有网络访问权限
3. **平台支持**: 支持 Android、iOS、Windows、macOS 和 Web 平台
4. **安全性**: WebView 启用了 JavaScript，请谨慎加载不信任的网站

## 示例代码

查看 `webview_example.dart` 文件获取完整的使用示例。

## 路由配置

WebView 路由已自动集成到应用的路由系统中：

- 路径: `/webview`
- 参数: `url` (必需), `title` (可选)
- 示例: `/webview?url=https%3A//example.com&title=Example`