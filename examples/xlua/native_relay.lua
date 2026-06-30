-- Copyright (c) 2026 Likeon. Licensed under the MIT License.
--
-- native_relay.lua — a thin Lua wrapper over the NativeRelay C# bridge.
-- All success / failure / error-code handling is funneled through M.request's onDone,
-- so business Lua never touches RelayCode branching directly.
--
-- Prerequisite: NativeRelayXLuaConfig.cs registered + xLua code generated.

local NR        = CS.Likeon.NativeRelay
local RelayCode = NR.RelayCode

local M = {}
local _bridge = nil

-- Convention: business code 1 = success (mirrors the C# quick-start sample).
-- Change to whatever your native side returns for "ok".
local OK_CODE = 1

-- Initialize once (e.g. at game boot). timeoutSeconds defaults to 5.
function M.init(timeoutSeconds)
    -- Pick a channel for the current platform (Editor/Win → Mock, Android → JNI, iOS → P/Invoke).
    local channel = NR.NativeChannelFactory.CreateForCurrentPlatform()
    -- The singleton dispatcher pumps the bridge every frame. Instance methods use ':' in Lua.
    _bridge = NR.MainThreadDispatcher.Instance:CreateBridge(channel, timeoutSeconds or 5.0)
end

-- Fire a request. onDone(ok, code, data):
--   ok == true  → success; data is the text/path string.
--   ok == false → check code: RelayCode.Timeout / RelayCode.Disposed / your business error code.
-- The callback runs on the MAIN thread (the bridge guarantees this), so the Lua VM is safe to touch.
function M.request(command, payload, onDone)
    if not _bridge then error("native_relay: call M.init() first") end

    _bridge:Request(command, payload, function(code, data)
        if code == RelayCode.Timeout then
            onDone(false, code, nil)        -- native never replied in time
        elseif code == RelayCode.Disposed then
            onDone(false, code, nil)        -- bridge was disposed
        elseif code == OK_CODE then
            onDone(true, code, data)        -- success
        else
            onDone(false, code, data)       -- business-defined error code
        end
    end)
end

-- Tear down (scene change / app shutdown). In-flight requests get the Disposed code.
function M.dispose()
    if _bridge then
        _bridge:Dispose()
        _bridge = nil
    end
end

return M
