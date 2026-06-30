# NativeRelay × xLua 接入示例

[English](README.md) · **简体中文**

用 **Lua**（经 [xLua](https://github.com/Tencent/xLua)）完整驱动 [NativeRelay](https://github.com/forestlii/NativeRelay)
桥的参考样板——所有业务调用，以及成功 / 失败 / 错误码处理，全都在 Lua 里。

> **状态 — 参考样板，未在本仓编译。** 本仓未装 xLua，这些文件不进 CI 构建/验证。其中 C# 签名
> 对齐 NativeRelay v0.2.0 的冻结公共契约。请在你自己的 xLua 工程里构建 + 真机验证。

## 文件

| 文件 | 作用 |
|---|---|
| [`NativeRelayXLuaConfig.cs`](NativeRelayXLuaConfig.cs) | xLua 代码生成配置 —— **必须**，见下方的"唯一大坑" |
| [`native_relay.lua`](native_relay.lua) | C# 桥的 Lua 薄封装；把所有错误码处理收口 |
| [`business_example.lua`](business_example.lua) | 纯 Lua 用法：设备信息 / 权限 / 定位 |

命令码（`Cmd.GetDeviceInfo` 等）来自 `relay_command.lua`，由 [`tools/codegen`](../../tools/codegen)
从 `commands.json` 生成——它是 C# / Java / Obj-C / Lua 四端命令码的单一真相源。**别手抄数字**。

## 接入（3 步）

1. 在 Unity 工程装 xLua。
2. 把 `NativeRelayXLuaConfig.cs` 放进 `Assets/`，执行菜单 **XLua → Generate Code**。
3. 把 `native_relay.lua` + `relay_command.lua` 放进你的 Lua require 路径，照 `business_example.lua`
   写业务逻辑。

## 人人都会踩的那个坑

在 **IL2CPP / AOT**（iOS、Android-IL2CPP）下，把 Lua 函数当 C# 的 `Action<int,string>` 传——
**运行时会抛异常**，除非该委托类型登记进 `[CSharpCallLua]` 并重新生成代码。编辑器（Mono，走反射）
下能跑，所以一路顺到真机才暴雷。`NativeRelayXLuaConfig.cs` 已登记它——别跳过代码生成那一步。

## 几个值得知道的点

- **线程**：`onResult` 由桥保证切回**主线程**才派发，所以 Lua VM（非线程安全）不会被并发触碰，
  你不用自己切线程。
- **分配**：每次 `request` 会生成一个 Lua 闭包 + 一个 C# 委托包装。原生能力（权限/拍照/定位）
  都是低频调用，这点分配无所谓——不在热路径上。
- **生命周期**：在途请求会被持有到结果或超时返回；中途 dispose 桥，会给每个未完成回调恰好一次
  `RelayCode.Disposed` 码收尾。
