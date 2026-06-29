# 原生能力 —— 设计

[English](native-capabilities.md) · **简体中文**

这是 NativeRelay-Native 里要实现的具体原生行为的**设计稿**。还没写代码——本文件先把契约定死
（命令码、payload/结果形状、每个能力归哪一层），免得实现时反复返工。各能力状态见下表。

## 两层结构（对应 干净层 / `.unity` 划分）

```
com.likeon.nativerelay          ← 干净层：纯原生能力，零 Unity 依赖
  ├─ .device, .net, .haptic …     （每个能力域一个子包）
com.likeon.nativerelay.unity    ← 绑定层：只放需要 UnityPlayer.currentActivity
                                   或 Activity/生命周期的代码（如 UI 选择器、阿里云这类 SDK）
```

iOS 没有包命名空间（C 符号是全局的），所以同样的两层用**函数名前缀 + 文件/target**表达：
干净层 = `NativeRelay_*`，绑定层 = `NativeRelayUnity_*`。

**为什么这套跟 NativeRelay 合得来：** `NativeRelayChannel.java` 走 JNI 代理回调
（`ResultCallback`），**不是** `UnityPlayer.UnitySendMessage`——它**没有任何 Unity import**（干净层
能力只用 `android.*`）。所以能力实现这层根本不必认识 Unity。绑定层**只在**某能力必须拿到 Unity 的 `Activity` /
一个 `UIViewController`（弹系统 UI，或某 SDK 要 `currentActivity`）时才出现。

**归层判据：** *它要不要弹系统 UI / 走 Activity result / 拿 `currentActivity`？* → **绑定层**；
否则 → **干净层**。

## 命令契约

命令码是 [`tools/codegen`](../tools/codegen/) 的真相源（一份 `commands.json` → C#/Java/Lua/ObjC）。
数值显式、绝不复用。

| cmd | 能力 | payload(输入) | code(返回) | data(返回) | 层 | iOS | Android |
|---|---|---|---|---|---|---|---|
| 1 | **RequestPermission** 权限请求 | 权限名：`camera`/`microphone`/`photos`/`location`/`notification` | 1=授权, 0=拒绝, 2=受限 | 状态文本 | **绑定**（Android 需 Activity） | 各框架授权 API | `ActivityCompat.requestPermissions` |
| 2 | **GetLocationOnce** 一次定位 | null（或精度提示） | 1=ok, 0=拒绝/失败, Timeout | json `{lat,lng,acc}` | 干净 | `CLLocationManager`（一次） | `LocationManager`（系统；**非** Fused——避免 Play Services 依赖） |
| 3 | **PickMedia** 选图/视频 | `image` / `video` | 1=选中, 0=取消 | 文件路径（拷进沙盒） | **绑定**（弹选择器） | `PHPickerViewController` | Photo Picker / `ACTION_PICK` |
| 4 | **SaveToAlbum** 存相册 | 要保存的文件路径 | 1=已存, 0=失败 | 资源 id（可选） | 干净 | `PHPhotoLibrary` | `MediaStore` |
| 5 | **CapturePhoto** 拍照 | null（或 `front`/`back`） | 1=拍到, 0=取消 | 文件路径 | **绑定**（弹相机） | `UIImagePickerController` | `ACTION_IMAGE_CAPTURE` |
| 6 | **ScanCode** 扫码 | null（或码制） | 1=扫到, 0=取消 | 扫到的字符串 | **绑定**（相机预览 UI） | `AVCaptureMetadataOutput` | CameraX + MLKit |
| 7 | **GetDeviceInfo** 设备信息 | null | 1=ok | json（见下） | 干净 | `UIDevice`/`UIScreen`/`Locale` | `Build`/`Configuration`/`DisplayMetrics` |
| 8 | **GetNetworkStatus** 网络状态 | null | 1=ok | `wifi`/`cellular`/`ethernet`/`none` | 干净 | `NWPathMonitor` | `ConnectivityManager` |
| 9 | **Vibrate** 振动 | 预设 `light`/`medium`/`heavy` 或 `ms:200` | 1=ok, 0=不支持 | — | 干净 | `UIImpactFeedbackGenerator`/`CoreHaptics` | `Vibrator`/`VibratorManager` |
| 10 | **OpenSettings** 跳设置 | `app` / `notification` / `store-review` | 1=已打开, 0=失败 | — | 干净* | `openURL`/`SKStoreReviewController` | `ACTION_APPLICATION_DETAILS_SETTINGS` |

\* `OpenSettings` 对 app/通知设置是干净的。**Android 的 `store-review` 需要 Play Core（一个 SDK）**
→ 那条路径归绑定层；应用内评分不是纯系统能力。

> 对应用户挑的清单编号：1→1、2→2、3→3、4→4、5→5、6→清单#7(扫码)、7→清单#11(设备信息)、
> 8→清单#12(网络)、9→清单#14(振动)、10→清单#15(跳设置)。

### 备注与诚实说明

- **权限是前置条件，不会自动给。** `GetLocationOnce` 需定位权限，`PickMedia`/`SaveToAlbum` 需相册权限，
  `CapturePhoto`/`ScanCode` 需相机权限。先调 `RequestPermission`；这些能力在缺权限时返回 `0`。
- **敏感权限不写进库 manifest。** `.aar` manifest 只带 `VIBRATE`（无害、install-time）。定位
  （`ACCESS_FINE/COARSE_LOCATION`）和 Q 以下的相册写入（`WRITE_EXTERNAL_STORAGE`，`maxSdkVersion=28`）
  是可选 + 敏感的，由你按需加进*你 app 的* manifest。iOS 需对应 Info.plist 用途串
  （`NSLocationWhenInUseUsageDescription`、`NSPhotoLibraryAddUsageDescription`）。
- **需 Activity 的能力（1 的安卓侧、3、5、6）要 Unity Activity。** 安卓上运行时权限请求和
  `startActivityForResult` 必须在 `Activity` 上发起、并接收结果回调——所以绑定层去取
  `UnityPlayer.currentActivity`。这正是 `.unity` 子包存在的理由。
- **`GetDeviceInfo` json**（拟）：`{model, manufacturer, os, osVersion, sdkInt?, lang, totalMemMB,
  screenW, screenH, dpi}`。**安全区 inset 需要 window/Activity**（iOS key window、Android
  `DisplayCutout`），故标为绑定层附加项，不进干净的设备信息调用。
- **线程：** 通道在调用线程外干活、带同一 seed 回调。弹 UI 的能力（3/5/6）必须切到**主线程**去弹，
  再把结果经回调送回（桥反正会再切回 Unity 主线程）。

## 实现规划（分批，每批可验证）

按"这台 Windows 机器上能不能真构建+验证"排序（Android 本机能构建；iOS 需 Mac）：

1. **批次 A —— 干净且轻量（Android 当场可验证）：✅ 已完成。** 7 设备信息、8 网络、9 振动、
   10 跳设置(app/通知)。无 Activity、无权限弹窗。Android 已实现 + `assembleRelease` 验证；
   iOS 参考源码已写（需 Mac）。
2. **批次 B —— 干净但需权限：✅ 已完成。** 2 一次定位（系统 `LocationManager`）、4 存相册（`MediaStore`）。
   Android 已实现 + `assembleRelease` 验证；iOS 参考（CoreLocation / Photos）已写——需 Mac。
3. **批次 C —— 绑定层（Activity/VC + 权限）：** 1 权限请求、3 选图、5 拍照、6 扫码。
   引入 `.unity` 子包 + `currentActivity` 管线。扫码最重（相机预览 UI）。

每批：在 codegen `commands.json` 定命令 → 实现干净/绑定 Java → 实现 ObjC → Android `assembleRelease`
验证可编译 → 提交。iOS 保持参考状态（待 Mac 验证）。

## 状态

- **批 A —— 已完成。** 设备信息 / 网络 / 振动 / 跳设置：Android 已实现并 **`assembleRelease` 验证**
  （能力类已进 `.aar`、`VIBRATE` 已并入 manifest、minSdk 23）；iOS 参考实现已写——**需 macOS/Xcode
  构建验证**。命令码由 `tools/codegen` 生成到四端。
- **批 B —— 已完成。** 一次定位（系统 `LocationManager`，无 Play Services）/ 存相册（`MediaStore`）：
  Android 已实现并 `assembleRelease` 验证；iOS 参考（CoreLocation / Photos）已写——需 macOS/Xcode。
- **批 C —— 待做**（需 Activity：权限请求 / 选图 / 拍照 / 扫码 + `.unity` 层）。

## 许可

MIT © 2026 Likeon
