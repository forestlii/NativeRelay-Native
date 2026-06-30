# NativeRelay-Native

**English** · [简体中文](README.zh-CN.md)

Native integration examples for the [NativeRelay](https://github.com/forestlii/NativeRelay)
Unity package. The NativeRelay core is **100% C#** and deliberately ships no native source;
the per-platform native side lives here as buildable reference projects you compile into a
binary and drop into your Unity app.

| Platform | Path | Output | Status |
|---|---|---|---|
| Android | [`android/`](android/) | `.aar` | Builds (verified: `assembleRelease` → `.aar`); on-device verification pending |
| iOS | [`ios/`](ios/) | `.h` + `.m` source (drop into `Assets/Plugins/iOS/`) | Reference impl; needs macOS/Xcode build + on-device verification |

> **Native capabilities** (permissions, location, media pickers, device info, vibrate, …) are
> designed in [docs/native-capabilities.md](docs/native-capabilities.md) — contract + the
> clean / `.unity` two-layer split. (Design fixed; implementation in batches.)

## Android

`android/` is an **Android Library** module implementing the Java contract that NativeRelay's
C# `AndroidChannel` talks to over JNI. It's a generic relay template — it handles the
seed / thread / callback plumbing; you fill in `handle()` per `command`.

- Class: `com.likeon.nativerelay.NativeRelayChannel`
- Contract: constructor `(ResultCallback)`, `send(long seed, int command, String payload)`,
  `dispose()`, inner interface `ResultCallback { void onResult(long seed, int code, String data); }`
- Source: [`android/nativerelay/src/main/java/com/likeon/nativerelay/NativeRelayChannel.java`](android/nativerelay/src/main/java/com/likeon/nativerelay/NativeRelayChannel.java)

> Keep the class / interface / method names exactly as-is — the C# side resolves them by
> reflection (`AndroidJavaObject` / `AndroidJavaProxy`). [`consumer-rules.pro`](android/nativerelay/consumer-rules.pro)
> ships keep rules inside the `.aar` so they survive R8 shrinking in the consuming app.

### Build the `.aar`

Requires **Android Studio** (JDK 17, Android SDK 34). Toolchain: AGP 8.5.2 / Gradle 8.7.

1. **Open** the `android/` folder in Android Studio and let it sync. (First open also
   generates the Gradle wrapper jar; or run `gradle wrapper --gradle-version 8.7` once if you
   have a system Gradle.)
2. Build the release library:
   ```
   cd android
   ./gradlew :nativerelay:assembleRelease       # Windows: gradlew.bat :nativerelay:assembleRelease
   ```
3. Grab the output:
   ```
   android/nativerelay/build/outputs/aar/nativerelay-release.aar
   ```

### Use it in Unity

1. Drop the `.aar` into your Unity project at **`Assets/Plugins/Android/`**.
2. Install the NativeRelay package (UPM git URL); on Android, `NativeChannelFactory`
   returns `AndroidChannel` automatically.
   ```csharp
   using Likeon.NativeRelay;

   var channel = NativeChannelFactory.CreateForCurrentPlatform();
   var bridge  = MainThreadDispatcher.Instance.CreateBridge(channel, timeoutSeconds: 5.0);
   bridge.Request((int)MyCommand.DoSomething, payload: null,
       onResult: (code, data) => { /* main thread: code + data (text/path) */ });
   ```

> Big binary (audio/image): don't push raw bytes through `data`. Save to a file natively and
> return the **path** as `data`; load it Unity-side.

## iOS

`ios/Source/` implements the C ABI that NativeRelay's C# `IosChannel` calls over P/Invoke
(`NativeRelayChannel_Init / _Send / _Dispose`). Integration is a **source drop-in**: copy the
`.h` + `.m` into `Assets/Plugins/iOS/` and Unity compiles them into the generated Xcode app —
no `.framework`/`.a` to manage. Building requires **macOS + Xcode**; see [`ios/`](ios/) for
details and the contract-alignment notes.

## Tools

- [`tools/codegen/`](tools/codegen/) — generate the `int` command codes for **C# / Java / Lua /
  Objective-C** from one `commands.json`, so a multi-platform project never hand-aligns them
  again. Includes a `--check` CI guard. See [tools/codegen/README.md](tools/codegen/README.md).

## Examples

- [`examples/xlua/`](examples/xlua/) — drive the bridge entirely from **Lua** via
  [xLua](https://github.com/Tencent/xLua): a thin Lua wrapper + the required xLua gen config +
  a pure-Lua business sample (all success/failure/error-code handling in Lua). Reference
  sample — not compiled in this repo. See [examples/xlua/README.md](examples/xlua/README.md).

## License

MIT © 2026 Likeon
