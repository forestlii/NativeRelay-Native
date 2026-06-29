# iOS native channel

**English** · [简体中文](README.zh-CN.md)

Reference implementation of the **C ABI contract** that NativeRelay's C# `IosChannel` talks to
over P/Invoke (`[DllImport("__Internal")]`). The C# side does the tricky part — a `GCHandle`
identifies the channel and a `[MonoPInvokeCallback]` static trampoline receives the result.
You implement three C functions; this folder gives you a generic relay template.

- Source: [`Source/NativeRelayChannel.h`](Source/NativeRelayChannel.h) (C ABI) +
  [`Source/NativeRelayChannel.m`](Source/NativeRelayChannel.m) (Objective-C impl)
- Contract: `NativeRelayChannel_Init / _Send / _Dispose` + callback
  `void (*)(void* context, long long seed, int code, const char* data)`

> Keep the **function names exactly** as-is — C# imports them by name. `context` is an opaque
> GCHandle C# hands in at `Init`; echo it back unchanged on every callback.

> ⚠️ **Status**: iOS builds require **macOS + Xcode**. This is a reference implementation
> carefully aligned to the contract (see the alignment notes in the file headers); it has
> **not been compiled / device-tested** here (authored on Windows). Build and verify it on a
> device in your iOS environment.

## Integrate (recommended: source drop-in)

iOS native plugins don't need to be precompiled — Unity compiles loose sources into the
generated Xcode app:

1. Copy both files into your Unity project under **`Assets/Plugins/iOS/`**.
2. Build for iOS. Unity statically links them, so `[DllImport("__Internal")]` resolves the
   three functions. No `.framework` / `.a` to manage.

Prefer a prebuilt binary (e.g. for closed-source distribution)? Wrap the same `.m`/`.h` in a
static library or `.framework` in Xcode and drop that under `Assets/Plugins/iOS/` instead.

## Use it in Unity

```csharp
using Likeon.NativeRelay;

// The factory returns IosChannel on iOS automatically.
var channel = NativeChannelFactory.CreateForCurrentPlatform();
var bridge  = MainThreadDispatcher.Instance.CreateBridge(channel, timeoutSeconds: 5.0);
bridge.Request((int)MyCommand.DoSomething, payload: null,
    onResult: (code, data) => { /* main thread: code + data (text/path) */ });
```

> Big binary (audio/image): return a **file path** as `data`, not raw bytes; load it Unity-side.

## License

MIT © 2026 Likeon
