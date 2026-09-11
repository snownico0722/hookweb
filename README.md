# HookWeb

HookWeb 是一个面向 Root + LSPosed 用户的 Android Web 内核扫描器。目标不是修改 App，而是先回答一件事：**手机里每个 App 集成了什么 Web 内核，真正运行时又用了什么内核。**

## 当前能力

### 1. APK 静态扫描

扫描已安装 App 的 base / split APK，识别：

- Android System WebView
- 腾讯 TBS / X5
- UC / U4
- 腾讯 XWeb
- GeckoView
- Crosswalk
- CEF
- App 自带 Chromium

静态结果显示为 `○`。它代表“APK 中存在对应 SDK / 类 / native 库特征”，**不等于当前一定实际启用了这个内核**。

### 2. ROOT 运行时扫描

通过 `su` 读取当前运行 App 的 `/proc/<pid>/maps`，检查已经映射的 Web 内核 native 库。运行时结果显示为 `●`。

HookWeb 只读取 APK 和进程映射；不会改目标 App 的文件，也不会遍历目标 App 的业务数据目录。

### 3. LSPosed API 102 运行时捕获

模块使用现代 libxposed API：

```text
io.github.libxposed:api:102.0.0
io.github.libxposed:service:102.0.0
```

HookWeb 会在已授权作用域的 App 中 Hook 常见 WebView 构造函数。当目标 App 真正创建 WebView 时：

1. 记录创建的是系统 WebView / TBS / UC / XWeb / GeckoView / Crosswalk / CEF 哪一层包装；
2. 在目标进程内读取 `/proc/self/maps`；
3. 把实际加载的 native core 一起回传给 HookWeb。

因此可以出现类似：

```text
京东
○ 腾讯 TBS / X5          APK 中集成 TBS
● 腾讯 TBS / X5          实际创建了 TBS WebView
● 系统 WebView           实际 native core 回退到了系统 WebView
```

这比只看 APK 里有没有 `com.tencent.smtt` 更接近真实情况。

## 使用流程

1. 安装 HookWeb。
2. 打开 App，点 **申请 ROOT**。
3. 点 **扫描 APK**。
4. 扫描结束后点 **申请 LSPosed 作用域**。HookWeb 会只请求本次扫描发现的 Web 候选 App，不默认把所有 App 都塞进作用域。
5. 重新打开你想确认的目标 App，并实际进入其 H5 / Web 页面。
6. 回到 HookWeb 点 **刷新 Hook 记录**，或点 **扫描运行中进程** 做 ROOT 二次确认。

LSPosed 服务未连接时，APK 静态扫描和 ROOT 运行时扫描仍然可用。

## 权限说明

- `QUERY_ALL_PACKAGES`：用于列出并扫描已安装 App。此项目定位为侧载 Root 工具，不面向 Google Play 发布。
- Root：通过标准 `su -c` 请求，兼容常见 Magisk / KernelSU / APatch su 环境。
- LSPosed：使用 libxposed API / service 102，不混用 legacy `de.robv.android.xposed` API。

## 已知限制

- APK 静态结果只能说明“集成/引用”，不能单独证明“当前正在使用”。
- 某些内核在运行后才通过动态 Dex / 插件 ClassLoader 下载和加载；如果 Java 包装层在 `onPackageReady` 之后才出现，第一版构造 Hook 可能漏掉，但 ROOT `/proc/maps` 仍可能识别实际 native core。
- 某些 App 只在特定页面创建 WebView，所以要实际打开对应 H5 页面后再看运行时结果。
- CEF 和深度魔改 Chromium 的类名/so 名称并不统一，目前使用通用特征，后续需要按真实样本继续补签名。
- 运行事件接收器是诊断通道，不作为安全边界；理论上其他本机 App 可以伪造事件，因此关键结论应优先结合 ROOT maps 交叉验证。

## 构建

项目基于：

- Android Gradle Plugin 9.2.1
- Gradle 9.4.1
- JDK 21
- compileSdk 37
- targetSdk 36
- minSdk 26

仓库自带 GitHub Actions，push / PR 后会构建 `app-debug.apk` 并上传为 `HookWeb-debug` artifact。

本地有对应 Android SDK 时也可以直接运行：

```bash
gradle :app:assembleDebug
```

## 下一步

第一阶段只做“看清楚”。等真实手机样本确认识别准确后，再做第二阶段：针对存在官方 fallback 的框架（优先 TBS/X5）提供**可选的强制 System WebView**，而不是直接把所有第三方内核对象暴力替换掉。
