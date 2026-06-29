# Native capabilities — design

**English** · [简体中文](native-capabilities.zh-CN.md)

This is the **design** for the concrete native behaviors implemented in NativeRelay-Native.
No code yet — this file fixes the contract (command codes, payload/result shapes, which layer
each lives in) so implementation doesn't churn. Status of each capability is tracked in the
table below.

## Two-layer structure (matches the clean / `.unity` split)

```
com.likeon.nativerelay          ← CLEAN layer: pure native ability, ZERO Unity dependency
  ├─ .device, .net, .haptic …     (one sub-package per ability area)
com.likeon.nativerelay.unity    ← BINDING layer: only code that needs UnityPlayer.currentActivity
                                   or the Activity/lifecycle (e.g. UI pickers, Aliyun-style SDKs)
```

iOS has no package namespace (C symbols are global), so the same split is expressed by
**function-name prefix + file/target**: clean = `NativeRelay_*`, binding = `NativeRelayUnity_*`.

**Why this works with NativeRelay:** `NativeRelayChannel.java` calls back through a JNI proxy
(`ResultCallback`), **not** `UnityPlayer.UnitySendMessage` — it has **no Unity imports** (clean-layer
capabilities use `android.*` only). So the ability implementation never has to know about Unity.
The binding layer exists **only** when a capability must reach the Unity `Activity` / a
`UIViewController` (to present system UI or to satisfy an SDK that needs `currentActivity`).

**Layer decision rule:** *Does it need to present system UI / go through an Activity result / get
`currentActivity`?* → **binding**. Otherwise → **clean**.

## Command contract

Command codes are the [`tools/codegen`](../tools/codegen/) source of truth (one `commands.json`
→ C#/Java/Lua/ObjC). Values are explicit and never reused.

| cmd | Capability | payload (in) | code (out) | data (out) | Layer | iOS | Android |
|---|---|---|---|---|---|---|---|
| 1 | **RequestPermission** | perm key: `camera`/`microphone`/`photos`/`location`/`notification` | 1=granted, 0=denied | status text | clean (proxy Activity) | per-framework authorization API | proxy Activity + `requestPermissions` |
| 2 | **GetLocationOnce** | null (or accuracy hint) | 1=ok, 0=denied/fail, Timeout | json `{lat,lng,acc}` | clean | `CLLocationManager` (one-shot) | `LocationManager` (system; **not** Fused — avoids a Play Services dep) |
| 3 | **PickMedia** | `image` / `video` | 1=picked, 0=cancelled | file path (copied to cache) | clean (proxy Activity) | `PHPickerViewController` | proxy Activity + `ACTION_GET_CONTENT` |
| 4 | **SaveToAlbum** | file path to save | 1=saved, 0=fail | saved asset id (opt) | clean | `PHPhotoLibrary` | `MediaStore` |
| 5 | **CapturePhoto** | null (or `front`/`back`) | 1=captured, 0=cancelled | file path | clean (proxy Activity) | `UIImagePickerController` | proxy Activity + `ACTION_IMAGE_CAPTURE` + own ContentProvider |
| 6 | **ScanCode** | null (or formats) | 1=scanned, 0=cancelled | scanned string | ⚠️ 3rd-party on Android | `AVCaptureMetadataOutput` (system) | **needs MLKit/ZXing** (no system intent) — stub |
| 7 | **GetDeviceInfo** | null | 1=ok | json (see below) | clean | `UIDevice`/`UIScreen`/`Locale` | `Build`/`Configuration`/`DisplayMetrics` |
| 8 | **GetNetworkStatus** | null | 1=ok | `wifi`/`cellular`/`ethernet`/`none` | clean | `NWPathMonitor` | `ConnectivityManager` |
| 9 | **Vibrate** | preset `light`/`medium`/`heavy` or `ms:200` | 1=ok, 0=unsupported | — | clean | `UIImpactFeedbackGenerator`/`CoreHaptics` | `Vibrator`/`VibratorManager` |
| 10 | **OpenSettings** | `app` / `notification` / `store-review` | 1=opened, 0=fail | — | clean* | `openURL`/`SKStoreReviewController` | `ACTION_APPLICATION_DETAILS_SETTINGS` |

\* `OpenSettings` is clean for app/notification settings. **`store-review` on Android needs Play
Core (an SDK)** → that path belongs to the binding layer; in-app review is not pure-system.

> Maps to the catalog the user picked: 1→1, 2→2, 3→3, 4→4, 5→5, 6→catalog#7 (ScanCode),
> 7→catalog#11 (DeviceInfo), 8→catalog#12 (Network), 9→catalog#14 (Vibrate), 10→catalog#15 (OpenSettings).

### Notes & honest caveats

- **Permission is a prerequisite, not automatic.** `GetLocationOnce` needs location permission,
  `PickMedia`/`SaveToAlbum` need photos permission, `CapturePhoto`/`ScanCode` need camera. Call
  `RequestPermission` first; these capabilities return `0` if permission is missing.
- **Sensitive permissions are NOT declared in the library manifest.** Only `VIBRATE` (harmless,
  install-time) ships in the `.aar` manifest. Location (`ACCESS_FINE/COARSE_LOCATION`) and pre-Q
  album writes (`WRITE_EXTERNAL_STORAGE`, `maxSdkVersion=28`) are optional + sensitive, so you add
  them to *your app's* manifest only if you use those capabilities. iOS needs the matching
  Info.plist usage strings (`NSLocationWhenInUseUsageDescription`, `NSPhotoLibraryAddUsageDescription`).
- **Correction — these capabilities turned out NOT to need the Unity Activity.** Permission / pick /
  capture do need *an* Activity (for `requestPermissions` / `startActivityForResult`), but the
  library ships its own **transparent proxy Activity** (`ui.NativeRelayProxyActivity`) that does the
  work in its own lifecycle and hands the result back via `PendingRequests`. It's launched from the
  application Context, so **no `UnityPlayer.currentActivity` and zero Unity coupling** — they stay
  clean-layer. The `.unity` binding sub-package is therefore *not* needed for these system
  capabilities; reserve it for SDKs that genuinely force `currentActivity` (e.g. Aliyun-style SDKs).
- **ScanCode is a stub on Android.** Android has no system barcode intent; a real scanner needs a
  third-party decoder (MLKit / ZXing) + camera preview, which breaks the zero-dependency rule — so
  `CodeScanner` returns a documented "not implemented" result. iOS *can* do it system-only
  (`AVCaptureMetadataOutput`), left as a reference stub there.
- **`CapturePhoto` uses the library's own `ContentProvider`** (`internal.NativeRelayFileProvider`)
  to hand the camera a `content://` output Uri — avoiding the androidx `FileProvider` dependency.
- **`GetDeviceInfo` json** (proposed): `{model, manufacturer, os, osVersion, sdkInt?, lang,
  totalMemMB, screenW, screenH, dpi}`. **Safe-area insets need a window/Activity** (iOS key
  window, Android `DisplayCutout`), so they're flagged as a binding-layer add-on, not part of the
  clean device-info call.
- **Threading:** the channel does work off the caller thread and calls back with the same seed.
  UI-presenting capabilities (3/5/6) must hop to the **main thread** to present, then deliver the
  result back through the callback (the bridge re-marshals to Unity's main thread anyway).

## Implementation plan (batches, each verifiable)

Ordered by "can I really build & verify it on this Windows machine" (Android builds here; iOS
needs a Mac):

1. **Batch A — clean & light (Android verifiable now): ✅ DONE.** 7 DeviceInfo, 8 Network,
   9 Vibrate, 10 OpenSettings(app/notification). No Activity, no permission prompts. Android
   implemented + `assembleRelease`-verified; iOS reference source written (needs a Mac).
2. **Batch B — clean but permission-gated: ✅ DONE.** 2 GetLocationOnce (system `LocationManager`),
   4 SaveToAlbum (`MediaStore`). Android implemented + `assembleRelease`-verified; iOS reference
   (CoreLocation / Photos) written — needs a Mac.
3. **Batch C — Activity-bound (via the proxy Activity, NOT `.unity`): ✅ DONE.** 1 RequestPermission,
   3 PickMedia, 5 CapturePhoto implemented Android-side with a transparent proxy Activity (no Unity
   coupling) + own ContentProvider; `assembleRelease`-verified. 6 ScanCode is a documented stub
   (needs MLKit/ZXing). iOS: permission is a real reference; pick/capture/scan are reference stubs
   (need a presented VC — verify on a Mac).

Each batch: define command in codegen `commands.json` → implement clean/binding Java → implement
ObjC → Android `assembleRelease` to verify it compiles → commit. iOS stays reference (Mac to verify).

## Status

- **Batch A — done.** GetDeviceInfo / GetNetworkStatus / Vibrate / OpenSettings: Android
  implemented and **`assembleRelease`-verified** (capability classes in the `.aar`, `VIBRATE` in
  the merged manifest, minSdk 23); iOS reference implementation written — **needs macOS/Xcode to
  build/verify**. Command codes generated into all four ends by `tools/codegen`.
- **Batch B — done.** GetLocationOnce (system `LocationManager`, no Play Services) /
  SaveToAlbum (`MediaStore`): Android implemented and `assembleRelease`-verified; iOS reference
  (CoreLocation / Photos) written — needs macOS/Xcode.
- **Batch C — done (Android).** RequestPermission / PickMedia / CapturePhoto via a transparent
  proxy Activity (`ui.NativeRelayProxyActivity`) + a zero-dep ContentProvider — **no Unity Activity,
  no androidx** — `assembleRelease`-verified. ScanCode is a documented stub (needs MLKit/ZXing). iOS:
  permission real reference; pick/capture/scan reference stubs (need a presented VC — verify on Mac).
- **Architecture note:** the proxy-Activity approach means the `.unity` binding layer is *not* needed
  for any of these 10 system capabilities — all clean. Reserve `.unity` for SDKs that force `currentActivity`.

All 10 capabilities are now implemented on Android (9 fully + ScanCode stub) and
`assembleRelease`-verified. Real-device verification (dialogs, camera, pickers actually returning)
is still the user's step — compilation ≠ runtime.

## License

MIT © 2026 Likeon
