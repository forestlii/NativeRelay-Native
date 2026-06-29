# iOS 原生通道

[English](README.md) · **简体中文**

NativeRelay 的 C# 侧 `IosChannel` 经 P/Invoke（`[DllImport("__Internal")]`）对接的 **C ABI 契约**
参考实现。C# 侧把难活做了——用 `GCHandle` 标识通道、用 `[MonoPInvokeCallback]` 静态跳板收结果。
你只需实现三个 C 函数；本目录给你一个通用中继模板。

- 源码：[`Source/NativeRelayChannel.h`](Source/NativeRelayChannel.h)（C ABI）+
  [`Source/NativeRelayChannel.m`](Source/NativeRelayChannel.m)（Objective-C 实现）
- 契约：`NativeRelayChannel_Init / _Send / _Dispose` + 回调
  `void (*)(void* context, long long seed, int code, const char* data)`

> **函数名必须原样保持**——C# 按名导入。`context` 是 C# 在 `Init` 传入的不透明 GCHandle；每次回调原样回传它。

> ⚠️ **状态**：iOS 构建需要 **macOS + Xcode**。这是一份严格对齐契约的参考实现（对齐说明见文件头），
> **未在此处编译 / 真机验证**（在 Windows 上编写）。请在你的 iOS 环境构建 + 真机验证。

## 集成（推荐：源码直接放入）

iOS 原生插件不必预编译——Unity 会把散放的源码编进生成的 Xcode 工程：

1. 把两个文件拷进 Unity 工程的 **`Assets/Plugins/iOS/`**。
2. 构建 iOS。Unity 静态链接它们，`[DllImport("__Internal")]` 即可解析三个函数。无需管理 `.framework` / `.a`。

想要预编译二进制（如闭源分发）？在 Xcode 里把同样的 `.m`/`.h` 包成静态库或 `.framework`，再放进
`Assets/Plugins/iOS/` 即可。

## 在 Unity 里用

```csharp
using Likeon.NativeRelay;

// 工厂在 iOS 上自动返回 IosChannel。
var channel = NativeChannelFactory.CreateForCurrentPlatform();
var bridge  = MainThreadDispatcher.Instance.CreateBridge(channel, timeoutSeconds: 5.0);
bridge.Request((int)MyCommand.DoSomething, payload: null,
    onResult: (code, data) => { /* 主线程：code + data（文本/路径） */ });
```

> 大块二进制（音频/图片）：把**文件路径**作为 `data` 返回，别传原始字节；Unity 侧再加载。

## 许可

MIT © 2026 Likeon
