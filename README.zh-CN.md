# NativeRelay-Native

[English](README.md) · **简体中文**

[NativeRelay](https://github.com/forestlii/NativeRelay) Unity 包的**原生集成示例**。NativeRelay
核心是 **100% C#**，刻意不带任何原生源码；各平台的原生侧放在这里，作为可构建的参考工程——你把它
编成二进制，丢进自己的 Unity 工程即可。

| 平台 | 路径 | 产物 | 状态 |
|---|---|---|---|
| Android | [`android/`](android/) | `.aar` | 可构建（已验证 `assembleRelease` → `.aar`）；真机验证待做 |
| iOS | [`ios/`](ios/) | `.h` + `.m` 源码（放入 `Assets/Plugins/iOS/`） | 参考实现；需 macOS/Xcode 构建 + 真机验证 |

## Android

`android/` 是一个 **Android Library** 模块，实现 NativeRelay 的 C# 侧 `AndroidChannel` 经 JNI
对接的 Java 契约。它是一个通用中继模板——seed / 线程 / 回调这些易错管线都替你做好；你只需按
`command` 在 `handle()` 里填真正的活。

- 类：`com.likeon.nativerelay.NativeRelayChannel`
- 契约：构造函数 `(ResultCallback)`、`send(long seed, int command, String payload)`、`dispose()`、
  内部接口 `ResultCallback { void onResult(long seed, int code, String data); }`
- 源码：[`android/nativerelay/src/main/java/com/likeon/nativerelay/NativeRelayChannel.java`](android/nativerelay/src/main/java/com/likeon/nativerelay/NativeRelayChannel.java)

> 类名 / 接口名 / 方法名必须**原样保持**——C# 侧靠反射（`AndroidJavaObject` / `AndroidJavaProxy`）
> 找它们。[`consumer-rules.pro`](android/nativerelay/consumer-rules.pro) 已把 keep 规则打进 `.aar`，
> 这样消费方开 R8 混淆时这些符号不会被改名/裁掉。

### 构建 `.aar`

需要 **Android Studio**（JDK 17、Android SDK 34）。工具链：AGP 8.5.2 / Gradle 8.7。

1. 用 Android Studio **打开 `android/` 目录**并等它 sync。（首次打开会顺带生成 Gradle wrapper jar；
   若本机有系统 Gradle，也可跑一次 `gradle wrapper --gradle-version 8.7`。）
2. 构建 release 库：
   ```
   cd android
   ./gradlew :nativerelay:assembleRelease       # Windows: gradlew.bat :nativerelay:assembleRelease
   ```
3. 取产物：
   ```
   android/nativerelay/build/outputs/aar/nativerelay-release.aar
   ```

### 在 Unity 里用

1. 把 `.aar` 放进 Unity 工程的 **`Assets/Plugins/Android/`**。
2. 装上 NativeRelay 包（UPM git URL）；在 Android 上 `NativeChannelFactory` 会自动返回
   `AndroidChannel`。
   ```csharp
   using Likeon.NativeRelay;

   var channel = NativeChannelFactory.CreateForCurrentPlatform();
   var bridge  = MainThreadDispatcher.Instance.CreateBridge(channel, timeoutSeconds: 5.0);
   bridge.Request((int)MyCommand.DoSomething, payload: null,
       onResult: (code, data) => { /* 主线程：code + data（文本/路径） */ });
   ```

> 大块二进制（音频/图片）：别把原始字节塞进 `data`。原生侧存成文件、把**路径**作为 `data` 回来，
> Unity 侧再按路径加载。

## iOS

`ios/Source/` 实现 NativeRelay 的 C# 侧 `IosChannel` 经 P/Invoke 调用的 C ABI
（`NativeRelayChannel_Init / _Send / _Dispose`）。集成方式是**源码直接放入**：把 `.h` + `.m` 拷进
`Assets/Plugins/iOS/`，Unity 会把它们编进生成的 Xcode 工程——无需管理 `.framework`/`.a`。
构建需 **macOS + Xcode**；详情与契约对照说明见 [`ios/`](ios/)。

## 工具

- [`tools/codegen/`](tools/codegen/) —— 用一份 `commands.json` 生成 **C# / Java / Lua /
  Objective-C** 四端的 `int` 命令码，让多端项目不再手工对齐；含 `--check` CI guard。
  详见 [tools/codegen/README.zh-CN.md](tools/codegen/README.zh-CN.md)。

## 许可

MIT © 2026 Likeon
