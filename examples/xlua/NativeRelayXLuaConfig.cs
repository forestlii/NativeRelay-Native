/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 *
 * xLua generation config for using NativeRelay from Lua.
 *
 * Drop this into your Unity project (anywhere under Assets/), then run
 * "XLua → Generate Code". WITHOUT this step, passing a Lua function as the
 * Action<int,string> onResult works in the Editor (Mono, reflection) but THROWS
 * on IL2CPP/AOT devices (iOS, Android-IL2CPP) — this is the #1 xLua integration trap.
 *
 * Reference sample: not compiled in this repo (no xLua here). Signatures match
 * NativeRelay's frozen public contract as of v0.2.0.
 */
using System;
using System.Collections.Generic;
using XLua;
using Likeon.NativeRelay;

public static class NativeRelayXLuaConfig
{
    // C# types Lua will call → generate wrappers so they survive IL2CPP stripping.
    [LuaCallCSharp]
    public static List<Type> LuaCallCSharp = new List<Type>
    {
        typeof(NativeChannelFactory),
        typeof(MainThreadDispatcher),
        typeof(Bridge),
        typeof(INativeChannel),
        typeof(RelayCode),
    };

    // Lua functions passed where a C# delegate is expected → MUST be registered,
    // or the lua-function → delegate adaption fails on AOT/IL2CPP at runtime.
    [CSharpCallLua]
    public static List<Type> CSharpCallLua = new List<Type>
    {
        typeof(Action<int, string>),   // = the onResult parameter of Bridge.Request
    };
}
