# NativeRelay-Native

**English** · [简体中文](README.zh-CN.md)

Native integration examples for the [NativeRelay](https://github.com/forestlii/NativeRelay)
Unity package. The NativeRelay core is **100% C#** and deliberately ships no native source;
the per-platform native side lives here as buildable reference projects you compile into a
binary and drop into your Unity app.

| Platform | Path | Output | Status |
|---|---|---|---|
| Android | [`android/`](android/) | `.aar` | Reference project — builds; on-device verification pending |
| iOS | `ios/` *(planned)* | `.framework` / `.a` | Not started |

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

## License

MIT © 2026 Likeon
